package com.primandproper.platform.analytics.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Mirrors platform-go's `analytics/noop/noop_test.go`. */
class NoopEventReporterTest {
    @Test
    fun `reporter is non-null`() {
        assertNotNull(NoopEventReporter)
    }

    @Test
    fun `close does not throw`() {
        NoopEventReporter.close()
    }

    @Test
    fun `addUser returns normally`() =
        runTest {
            NoopEventReporter.addUser("user123", mapOf("key" to "value"))
        }

    @Test
    fun `eventOccurred returns normally`() =
        runTest {
            NoopEventReporter.eventOccurred("event_name", "user123", mapOf("key" to "value"))
        }

    @Test
    fun `eventOccurredAnonymous returns normally`() =
        runTest {
            NoopEventReporter.eventOccurredAnonymous("event_name", "anon123", mapOf("key" to "value"))
        }
}
