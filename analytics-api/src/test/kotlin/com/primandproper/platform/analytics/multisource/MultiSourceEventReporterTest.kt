package com.primandproper.platform.analytics.multisource

import com.primandproper.platform.analytics.EventReporter
import com.primandproper.platform.analytics.mock.EventReporterMock
import com.primandproper.platform.analytics.noop.NoopEventReporter
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Mirrors platform-go's `analytics/multisource/reporter_test.go`. */
class MultiSourceEventReporterTest {
    private class Boom : RuntimeException("arbitrary")

    private fun recording(reporters: Map<String, EventReporter>): Pair<MultiSourceEventReporter, RecordingObserver> {
        val obs = RecordingObserver()
        return MultiSourceEventReporter(reporters, obs) to obs
    }

    @Test
    fun `constructor with null reporters yields empty map`() {
        val r = MultiSourceEventReporter(null as Map<String, EventReporter>?, null, null)
        assertNotNull(r)
        assertTrue(r.reporters.isEmpty())
    }

    @Test
    fun `constructor with populated map`() {
        val r = MultiSourceEventReporter(mapOf("ios" to NoopEventReporter))
        assertEquals(1, r.reporters.size)
    }

    @Test
    fun `close closes every underlying reporter`() {
        val ios = EventReporterMock(closeFunc = {})
        val web = EventReporterMock(closeFunc = {})
        MultiSourceEventReporter(mapOf("ios" to ios, "web" to web)).close()
        assertEquals(1, ios.closeCalls.size)
        assertEquals(1, web.closeCalls.size)
    }

    @Test
    fun `close closes a shared reporter exactly once`() {
        val shared = EventReporterMock(closeFunc = {})
        MultiSourceEventReporter(mapOf("ios" to shared, "web" to shared)).close()
        assertEquals(1, shared.closeCalls.size)
    }

    @Test
    fun `getReporter returns reporter for known source`() {
        val expected = NoopEventReporter
        val m = MultiSourceEventReporter(mapOf("ios" to expected))
        assertSame(expected, m.getReporter("ios"))
    }

    @Test
    fun `getReporter returns noop for unknown source`() {
        val m = MultiSourceEventReporter()
        assertSame(NoopEventReporter, m.getReporter("unknown"))
    }

    @Test
    fun `trackEvent delegates to correct reporter and records values`() =
        runTest {
            val mock =
                EventReporterMock(
                    eventOccurredFunc = { event, userID, properties ->
                        assertEquals("signup", event)
                        assertEquals("user1", userID)
                        assertEquals("ios", properties[SOURCE_PROPERTY_KEY])
                        assertEquals("pro", properties["plan"])
                    },
                )
            val (m, obs) = recording(mapOf("ios" to mock))

            m.trackEvent("ios", "signup", "user1", mapOf("plan" to "pro"))

            assertEquals(1, mock.eventOccurredCalls.size)
            obs.assertObservedOperationWithValues(
                SOURCE_PROPERTY_KEY to "ios",
                "event" to "signup",
                "user_id" to "user1",
            )
        }

    @Test
    fun `trackEvent uses noop for unknown source`() =
        runTest {
            MultiSourceEventReporter().trackEvent("unknown", "signup", "user1")
        }

    @Test
    fun `trackEvent records values even when reporter throws`() =
        runTest {
            val mock = EventReporterMock(eventOccurredFunc = { _, _, _ -> throw Boom() })
            val (m, obs) = recording(mapOf("ios" to mock))

            assertFailsWith<Boom> { m.trackEvent("ios", "signup", "user1") }

            obs.assertObservedOperationWithValues(
                SOURCE_PROPERTY_KEY to "ios",
                "event" to "signup",
                "user_id" to "user1",
            )
        }

    @Test
    fun `trackAnonymousEvent delegates to correct reporter`() =
        runTest {
            val mock =
                EventReporterMock(
                    eventOccurredAnonymousFunc = { event, anonymousID, properties ->
                        assertEquals("page_view", event)
                        assertEquals("anon1", anonymousID)
                        assertEquals("web", properties[SOURCE_PROPERTY_KEY])
                    },
                )
            val (m, obs) = recording(mapOf("web" to mock))

            m.trackAnonymousEvent("web", "page_view", "anon1")

            assertEquals(1, mock.eventOccurredAnonymousCalls.size)
            obs.assertObservedOperationWithValues(
                SOURCE_PROPERTY_KEY to "web",
                "event" to "page_view",
                "anonymous_id" to "anon1",
            )
        }

    @Test
    fun `addUser delegates to correct reporter`() =
        runTest {
            val mock =
                EventReporterMock(
                    addUserFunc = { userID, properties ->
                        assertEquals("user1", userID)
                        assertEquals("ios", properties[SOURCE_PROPERTY_KEY])
                        assertEquals("pro", properties["plan"])
                    },
                )
            val (m, obs) = recording(mapOf("ios" to mock))

            m.addUser("ios", "user1", mapOf("plan" to "pro"))

            assertEquals(1, mock.addUserCalls.size)
            obs.assertObservedOperationWithValues(SOURCE_PROPERTY_KEY to "ios", "user_id" to "user1")
        }

    @Test
    fun `addUser records values even when reporter throws`() =
        runTest {
            val mock = EventReporterMock(addUserFunc = { _, _ -> throw Boom() })
            val (m, obs) = recording(mapOf("ios" to mock))

            assertFailsWith<Boom> { m.addUser("ios", "user1") }

            obs.assertObservedOperationWithValues(SOURCE_PROPERTY_KEY to "ios", "user_id" to "user1")
        }

    @Test
    fun `withSourceProperty adds source to empty properties`() {
        val result = withSourceProperty("ios", emptyMap())
        assertEquals("ios", result[SOURCE_PROPERTY_KEY])
        assertEquals(1, result.size)
    }

    @Test
    fun `withSourceProperty does not mutate original`() {
        val original = mapOf<String, Any?>("key" to "value")
        val result = withSourceProperty("web", original)
        assertEquals("web", result[SOURCE_PROPERTY_KEY])
        assertEquals("value", result["key"])
        assertEquals(2, result.size)
        assertFalse(original.containsKey(SOURCE_PROPERTY_KEY))
    }
}
