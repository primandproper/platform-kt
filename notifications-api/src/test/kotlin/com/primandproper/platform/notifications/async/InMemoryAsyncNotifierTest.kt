package com.primandproper.platform.notifications.async

import com.primandproper.platform.notifications.NotificationKeys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Span
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Exercises the in-memory async notifier's delivery ordering and retry, standing in for the networked
 * backends platform-go's `async` package ships. Uses a RecordingObserver so a test can assert what
 * each publish observed without a live tracer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryAsyncNotifierTest {
    private fun recording(maxAttempts: Int = DEFAULT_MAX_ATTEMPTS): Pair<InMemoryAsyncNotifier, RecordingObserver> {
        val obs = RecordingObserver()
        return InMemoryAsyncNotifier(obs, maxAttempts, kotlin.time.Duration.ZERO) to obs
    }

    @Test
    fun `events are delivered to a subscriber in publish order`() =
        runTest {
            val (notifier, _) = recording()
            val received = mutableListOf<String>()
            notifier.subscribe("room") { received += it.type }

            notifier.publish("room", AsyncEvent("a"))
            notifier.publish("room", AsyncEvent("b"))
            notifier.publish("room", AsyncEvent("c"))

            assertEquals(listOf("a", "b", "c"), received)
        }

    @Test
    fun `publish only reaches subscribers of the same channel`() =
        runTest {
            val (notifier, _) = recording()
            val room = mutableListOf<String>()
            val lobby = mutableListOf<String>()
            notifier.subscribe("room") { room += it.type }
            notifier.subscribe("lobby") { lobby += it.type }

            notifier.publish("room", AsyncEvent("x"))

            assertEquals(listOf("x"), room)
            assertTrue(lobby.isEmpty())
        }

    @Test
    fun `a transient failure is retried and then delivered exactly once`() =
        runTest {
            val (notifier, _) = recording(maxAttempts = 3)
            var attempts = 0
            var delivered = 0
            notifier.subscribe("room") {
                attempts++
                if (attempts < 3) throw RuntimeException("transient")
                delivered++
            }

            notifier.publish("room", AsyncEvent("a"))

            assertEquals(3, attempts)
            assertEquals(1, delivered)
        }

    @Test
    fun `exhausting the retry budget fails the publish and records the error`() =
        runTest {
            val (notifier, obs) = recording(maxAttempts = 3)
            var attempts = 0
            notifier.subscribe("room") {
                attempts++
                throw RuntimeException("always")
            }

            assertFailsWith<RuntimeException> { notifier.publish("room", AsyncEvent("a")) }

            assertEquals(3, attempts)
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `publish observes the channel and event type`() =
        runTest {
            val (notifier, obs) = recording()
            notifier.subscribe("room") { }

            notifier.publish("room", AsyncEvent("joined"))

            obs.assertObservedOperationWithValues(
                NotificationKeys.CHANNEL to "room",
                NotificationKeys.EVENT_TYPE to "joined",
            )
        }

    @Test
    fun `a cancelled subscription no longer receives events`() =
        runTest {
            val (notifier, _) = recording()
            val received = mutableListOf<String>()
            val sub = notifier.subscribe("room") { received += it.type }

            notifier.publish("room", AsyncEvent("a"))
            sub.cancel()
            notifier.publish("room", AsyncEvent("b"))

            assertEquals(listOf("a"), received)
        }

    @Test
    fun `events flow delivers published events in order`() =
        runTest {
            val (notifier, _) = recording()
            val received = mutableListOf<String>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    notifier.events("room").collect { received += it.type }
                }
            advanceUntilIdle()

            notifier.publish("room", AsyncEvent("a"))
            notifier.publish("room", AsyncEvent("b"))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), received)
            job.cancelAndJoin()
        }

    @Test
    fun `cancelling the events collector unsubscribes so later publishes are not received`() =
        runTest {
            val (notifier, _) = recording()
            val received = mutableListOf<String>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    notifier.events("room").collect { received += it.type }
                }
            advanceUntilIdle()
            notifier.publish("room", AsyncEvent("a"))
            advanceUntilIdle()

            job.cancelAndJoin()
            // awaitClose has unsubscribed; a subsequent publish reaches nobody.
            notifier.publish("room", AsyncEvent("b"))
            advanceUntilIdle()

            assertEquals(listOf("a"), received)
        }

    @Test
    fun `events flow only sees events for its channel`() =
        runTest {
            val (notifier, _) = recording()
            val received = mutableListOf<String>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    notifier.events("room").collect { received += it.type }
                }
            advanceUntilIdle()

            notifier.publish("lobby", AsyncEvent("x"))
            notifier.publish("room", AsyncEvent("y"))
            advanceUntilIdle()

            assertEquals(listOf("y"), received)
            job.cancelAndJoin()
        }

    @Test
    fun `intermediate retry failures are logged at warn`() =
        runTest {
            val logger = RecordingLogger()
            val notifier = InMemoryAsyncNotifier(maxAttempts = 3, retryDelay = Duration.ZERO, logger = logger)
            var attempts = 0
            notifier.subscribe("room") {
                attempts++
                if (attempts < 3) throw RuntimeException("transient")
            }

            notifier.publish("room", AsyncEvent("a"))

            // Two intermediate failures are warned; the third attempt succeeds and is not logged.
            assertEquals(2, logger.warnings.size)
        }

    /** A [Logger] that captures warn messages so a test can assert intermediate retry failures surface. */
    private class RecordingLogger : Logger {
        val warnings: MutableList<String> = mutableListOf()

        override fun debug(msg: String) {}

        override fun info(msg: String) {}

        override fun warn(msg: String) {
            warnings += msg
        }

        override fun error(
            whatWasHappening: String,
            err: Throwable?,
        ) {}

        override fun withName(name: String): Logger = this

        override fun withValue(
            key: String,
            value: Any?,
        ): Logger = this

        override fun withValues(values: Map<String, Any?>): Logger = this

        override fun withError(err: Throwable): Logger = this

        override fun withSpan(span: Span): Logger = this

        override fun clone(): Logger = this
    }

    @Test
    fun `close drops all subscribers`() =
        runTest {
            val (notifier, _) = recording()
            val received = mutableListOf<String>()
            notifier.subscribe("room") { received += it.type }

            notifier.close()
            notifier.publish("room", AsyncEvent("a"))

            assertTrue(received.isEmpty())
        }
}
