package com.primandproper.platform.observability.testing

import com.primandproper.platform.observability.span
import kotlinx.coroutines.test.runTest
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
}
