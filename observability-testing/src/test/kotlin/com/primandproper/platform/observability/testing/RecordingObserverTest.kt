package com.primandproper.platform.observability.testing

import com.primandproper.platform.observability.span
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordingObserverTest {
    @Test
    fun setLandsOnBothPillarsWhileSpanOnlyAndLogOnlyDoNot() =
        runTest {
            val observer = RecordingObserver()

            observer.span("add") {
                set("group_id" to "g1", "member_id" to "m1")
                spanOnly("trace_only", 1)
                logOnly("log_only", 2)
            }

            val op = observer.operations.single()
            assertTrue(op.ended, "span scope must end the operation")

            // set() reaches both pillars.
            assertEquals("g1", op.spanValues["group_id"])
            assertEquals("g1", op.logValues["group_id"])
            op.assertObserved(observedKeyValue("group_id", "g1").onSpan())
            op.assertObserved(observedKeyValue("group_id", "g1").onLog())

            // spanOnly reaches the span but not the log; logOnly the reverse.
            op.assertObserved(observedKey("trace_only").onSpan())
            assertFailsWith<AssertionError> { op.assertObserved(observedKey("trace_only").onLog()) }
            op.assertObserved(observedKey("log_only").onLog())
            assertFailsWith<AssertionError> { op.assertObserved(observedKey("log_only").onSpan()) }
        }

    @Test
    fun spanScopeRecordsAndRethrowsThrownException() =
        runTest {
            val observer = RecordingObserver()
            val boom = IllegalStateException("boom")

            val thrown =
                assertFailsWith<IllegalStateException> {
                    observer.span("explode") { throw boom }
                }

            assertSame(boom, thrown)
            val op = observer.operations.single()
            assertTrue(op.ended, "span scope must end even when the block throws")
            assertTrue(boom in op.errors, "thrown exception must be recorded")
        }

    @Test
    fun streamOrdersObservationsAcrossOperations() =
        runTest {
            val observer = RecordingObserver()
            observer.span("first") { set("a" to 1) }
            observer.span("second") { set("b" to 2) }

            observer.assertObservedInOrder(observedKey("a"), observedKey("b"))
            observer.assertObservedOperationWithValues("b" to 2)
        }

    @Test
    fun concurrentSetAndStreamDoNotThrow() {
        // Regression: RecordingOperation's state was unsynchronized, so a set() racing a stream()
        // read threw ConcurrentModificationException. Hammer one operation from many threads while a
        // reader repeatedly snapshots the global stream, and assert nothing throws and nothing is lost.
        val observer = RecordingObserver()
        val op = observer.begin("concurrent")

        val writers = 8
        val perWriter = 2000
        val failures = ConcurrentLinkedQueue<Throwable>()
        val pool = Executors.newFixedThreadPool(writers + 1)
        val start = CountDownLatch(1)
        val done = CountDownLatch(writers)
        val readerRunning = AtomicBoolean(true)

        pool.submit {
            start.await()
            while (readerRunning.get()) {
                try {
                    observer.stream()
                } catch (t: Throwable) {
                    failures += t
                }
            }
        }
        repeat(writers) { w ->
            pool.submit {
                try {
                    start.await()
                    repeat(perWriter) { i -> op.set("k$w-$i", i) }
                } catch (t: Throwable) {
                    failures += t
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        done.await()
        readerRunning.set(false)
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        assertTrue(failures.isEmpty(), "concurrent recording/reading must not throw: ${failures.firstOrNull()}")
        val recorded = observer.operations.single()
        // Every set() (unique key) recorded exactly one observation and one value.
        assertEquals(writers * perWriter, recorded.observations.size)
        assertEquals(writers * perWriter, recorded.values.size)
    }
}
