package com.primandproper.platform.analytics.segment

import com.primandproper.platform.circuitbreaking.CircuitBrokenException
import com.primandproper.platform.circuitbreaking.CircuitState
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.identifiers.newUuid
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.testing.RecordingObserver
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.Message
import com.segment.analytics.messages.MessageBuilder
import com.segment.analytics.messages.TrackMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors platform-go's `analytics/segment/segment_test.go`. */
class SegmentEventReporterTest {
    private class Boom : RuntimeException("delivery boom")

    /** A reporter with a RecordingObserver and a capturing enqueuer, so tests assert adaptation offline. */
    private fun recording(
        breaker: com.primandproper.platform.circuitbreaking.CircuitBreaker = NoopCircuitBreaker,
        onEnqueue: (MessageBuilder<*, *>) -> Unit = {},
    ): Triple<SegmentEventReporter, RecordingObserver, MutableList<MessageBuilder<*, *>>> {
        val captured = mutableListOf<MessageBuilder<*, *>>()
        val obs = RecordingObserver()
        val reporter =
            SegmentEventReporter(
                o11y = obs,
                circuitBreaker = breaker,
                enqueuer = {
                    captured += it
                    onEnqueue(it)
                },
                closer = {},
            )
        return Triple(reporter, obs, captured)
    }

    @Test
    fun `constructor with valid token returns non-null`() =
        runTest {
            val reporter = SegmentEventReporter(apiToken = "test-token")
            assertNotNull(reporter)
            reporter.close()
        }

    @Test
    fun `constructor with empty token throws`() {
        assertFailsWith<EmptyApiTokenException> { SegmentEventReporter(apiToken = "") }
    }

    @Test
    fun `close does not throw`() =
        runTest {
            SegmentEventReporter(apiToken = "test-token").close()
        }

    @Test
    fun `addUser enqueues an identify and observes the user id`() =
        runTest {
            val (reporter, obs, captured) = recording()
            val userID = newUuid()

            reporter.addUser(userID, mapOf("plan" to "pro"))

            obs.assertObservedOperationWithValues(Keys.USER_ID to userID)
            val msg = captured.single().build()
            assertTrue(msg is IdentifyMessage)
            assertEquals(userID, msg.userId())
        }

    @Test
    fun `eventOccurred enqueues a track for the identified user`() =
        runTest {
            val (reporter, obs, captured) = recording()
            val userID = newUuid()

            reporter.eventOccurred("signup", userID, mapOf("k" to "v"))

            obs.assertObservedOperationWithValues("event" to "signup", Keys.USER_ID to userID)
            val msg = captured.single().build()
            assertTrue(msg is TrackMessage)
            assertEquals("signup", msg.event())
            assertEquals(userID, msg.userId())
        }

    @Test
    fun `eventOccurredAnonymous enqueues a track with an anonymous id`() =
        runTest {
            val (reporter, obs, captured) = recording()
            val anonymousID = newUuid()

            reporter.eventOccurredAnonymous("page_view", anonymousID)

            obs.assertObservedOperationWithValues("event" to "page_view", Keys.USER_ID to anonymousID)
            val msg = captured.single().build() as TrackMessage
            assertEquals("page_view", msg.event())
            assertEquals(anonymousID, msg.anonymousId())
        }

    @Test
    fun `open circuit rejects and enqueues nothing`() =
        runTest {
            val breaker = RecordingCircuitBreaker(reject = true)
            val (reporter, _, captured) = recording(breaker = breaker)

            val error = assertFailsWith<Throwable> { reporter.addUser(newUuid()) }
            assertTrue(error is CircuitBrokenException)
            assertTrue(captured.isEmpty())
            assertEquals(1, breaker.rejectionCount)
        }

    @Test
    fun `enqueue failure trips the breaker`() =
        runTest {
            val breaker = RecordingCircuitBreaker()
            val (reporter, _, _) = recording(breaker = breaker, onEnqueue = { throw Boom() })

            assertFailsWith<Boom> { reporter.eventOccurred("signup", newUuid()) }
            assertEquals(1, breaker.failureCount)
            assertEquals(CircuitState.CLOSED, breaker.state.value)
        }

    /** A message to hand the callback; its content is irrelevant since the callback never logs it. */
    private fun anyMessage(): Message = TrackMessage.builder("e").userId(newUuid()).build()

    @Test
    fun `delivery failure logs an error and counts a breaker failure`() {
        val breaker = RecordingCircuitBreaker()
        val logger = CapturingLogger()
        val callback = SegmentDeliveryCallback(logger, breaker)

        callback.failure(anyMessage(), Boom())

        assertEquals(1, breaker.failureCount)
        assertEquals(0, breaker.successCount)
        assertNotNull(logger.lastError, "delivery failure must be logged")
        assertTrue(logger.lastError is Boom)
    }

    @Test
    fun `delivery success counts a breaker success and logs nothing`() {
        val breaker = RecordingCircuitBreaker()
        val logger = CapturingLogger()
        val callback = SegmentDeliveryCallback(logger, breaker)

        callback.success(anyMessage())

        assertEquals(1, breaker.successCount)
        assertEquals(0, breaker.failureCount)
        assertNull(logger.lastError, "a delivered batch must not log an error")
    }

    @Test
    fun `delivery outcome on an open breaker is swallowed`() {
        val breaker = RecordingCircuitBreaker(reject = true)
        val callback = SegmentDeliveryCallback(NoopLogger, breaker)

        // The breaker rejects; the callback must not let that escape onto the SDK's flush thread.
        callback.failure(anyMessage(), Boom())
        callback.success(anyMessage())

        assertEquals(2, breaker.rejectionCount)
    }

    /** A [Logger] that captures the most recent [error] throwable so a test can assert on it. */
    private class CapturingLogger(
        var lastError: Throwable? = null,
    ) : Logger by NoopLogger {
        override fun error(
            whatWasHappening: String,
            err: Throwable?,
        ) {
            lastError = err
        }
    }
}
