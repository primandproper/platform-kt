package com.primandproper.platform.analytics.android

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BufferingEventReporterTest {
    private fun recording(capacity: Int = DEFAULT_BUFFER_CAPACITY): Pair<BufferingEventReporter, RecordingObserver> {
        val obs = RecordingObserver()
        return BufferingEventReporter(obs, capacity) to obs
    }

    @Test
    fun `addUser buffers an identify and observes the user id`() =
        runTest {
            val (reporter, obs) = recording()

            reporter.addUser("user1", mapOf("plan" to "pro"))

            obs.assertObservedOperationWithValues(Keys.USER_ID to "user1")
            val event = reporter.snapshot().single()
            assertTrue(event is BufferedEvent.Identify)
            assertEquals("user1", event.userID)
            assertEquals("pro", event.properties["plan"])
        }

    @Test
    fun `eventOccurred buffers an identified track`() =
        runTest {
            val (reporter, obs) = recording()

            reporter.eventOccurred("signup", "user1", mapOf("k" to "v"))

            obs.assertObservedOperationWithValues("event" to "signup", Keys.USER_ID to "user1")
            val event = reporter.snapshot().single() as BufferedEvent.Track
            assertEquals("signup", event.event)
            assertEquals("user1", event.userID)
            assertTrue(!event.anonymous)
        }

    @Test
    fun `eventOccurredAnonymous buffers an anonymous track`() =
        runTest {
            val (reporter, _) = recording()

            reporter.eventOccurredAnonymous("page_view", "anon1")

            val event = reporter.snapshot().single() as BufferedEvent.Track
            assertEquals("page_view", event.event)
            assertEquals("anon1", event.userID)
            assertTrue(event.anonymous)
        }

    @Test
    fun `drain returns and clears buffered events`() =
        runTest {
            val (reporter, _) = recording()
            reporter.eventOccurred("a", "u")
            reporter.eventOccurred("b", "u")

            val drained = reporter.drain()
            assertEquals(2, drained.size)
            assertEquals(0, reporter.size)
            assertTrue(reporter.snapshot().isEmpty())
        }

    @Test
    fun `buffer is bounded, dropping oldest`() =
        runTest {
            val (reporter, _) = recording(capacity = 2)
            reporter.eventOccurred("first", "u")
            reporter.eventOccurred("second", "u")
            reporter.eventOccurred("third", "u")

            val events = reporter.snapshot().map { (it as BufferedEvent.Track).event }
            assertEquals(listOf("second", "third"), events)
        }

    @Test
    fun `close clears the buffer and ignores further events`() =
        runTest {
            val (reporter, _) = recording()
            reporter.eventOccurred("a", "u")

            reporter.close()
            assertEquals(0, reporter.size)

            reporter.eventOccurred("b", "u")
            assertEquals(0, reporter.size)
        }
}
