package com.primandproper.platform.featureflags.mock

import com.primandproper.platform.featureflags.EvaluationContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the programmable mock double, mirroring platform-go's `featureflags/mock` usage. */
class MockFeatureFlagManagerTest {
    private fun evalCtx() = EvaluationContext(targetingKey = "user-id")

    @Test
    fun `delegates to configured function and records the call`() =
        runTest {
            val mock =
                MockFeatureFlagManager().apply {
                    canUseFeatureFn = { _, _ -> true }
                }
            assertTrue(mock.canUseFeature("beta", evalCtx()))
            assertEquals(1, mock.canUseFeatureCalls.size)
            assertEquals("beta", mock.canUseFeatureCalls.first().feature)
        }

    @Test
    fun `defaults behave like noop`() =
        runTest {
            val mock = MockFeatureFlagManager()
            assertEquals(false, mock.canUseFeature("f", evalCtx()))
            assertEquals("d", mock.getStringValue("f", "d", evalCtx()))
            assertEquals(9L, mock.getInt64Value("f", 9L, evalCtx()))
            assertEquals(1.5, mock.getFloat64Value("f", 1.5, evalCtx()), 1e-9)
            assertEquals(null, mock.getObjectValue("f", null, evalCtx()))
        }

    @Test
    fun `records close calls`() {
        val mock = MockFeatureFlagManager()
        mock.close()
        mock.close()
        assertEquals(2, mock.closeCalls)
    }
}
