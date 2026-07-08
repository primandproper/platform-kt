package com.primandproper.platform.analytics.segment

import com.primandproper.platform.circuitbreaking.CircuitState
import com.primandproper.platform.circuitbreaking.ErrCircuitBroken
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.identifiers.newUuid
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.MessageBuilder
import com.segment.analytics.messages.TrackMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
    fun `constructor with valid token returns non-null`() {
        val reporter = SegmentEventReporter(apiToken = "test-token")
        assertNotNull(reporter)
        reporter.close()
    }

    @Test
    fun `constructor with empty token throws`() {
        assertFailsWith<EmptyApiTokenException> { SegmentEventReporter(apiToken = "") }
    }

    @Test
    fun `close does not throw`() {
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
            assertEquals(ErrCircuitBroken, error)
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
}
