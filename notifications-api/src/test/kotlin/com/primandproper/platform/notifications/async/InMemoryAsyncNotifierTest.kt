package com.primandproper.platform.notifications.async

import com.primandproper.platform.notifications.NotificationKeys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the in-memory async notifier's delivery ordering and retry, standing in for the networked
 * backends platform-go's `async` package ships. Uses a RecordingObserver so a test can assert what
 * each publish observed without a live tracer.
 */
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
