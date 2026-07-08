package com.primandproper.platform.eventstream.noop

import com.primandproper.platform.eventstream.Event
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `eventstream/noop/noop_test.go`. */
class NoopEventStreamTest {
    @Test
    fun `send is a no-op`() =
        runTest {
            NoopEventStream().send(Event("test", """{"key":"value"}"""))
        }

    @Test
    fun `done completes only after close`() =
        runTest {
            val s = NoopEventStream()
            assertFalse(s.done.isCompleted)
            s.close()
            assertTrue(s.done.isCompleted)
        }

    @Test
    fun `close is idempotent`() =
        runTest {
            val s = NoopEventStream()
            s.close()
            s.close()
            assertTrue(s.done.isCompleted)
        }

    @Test
    fun `bidirectional send is a no-op and receive is available`() =
        runTest {
            val s = NoopBidirectionalEventStream()
            s.send(Event("test"))
            // receive() never emits; we only assert it is obtainable, mirroring the Go test.
            s.receive()
            s.close()
            assertTrue(s.done.isCompleted)
        }
}
