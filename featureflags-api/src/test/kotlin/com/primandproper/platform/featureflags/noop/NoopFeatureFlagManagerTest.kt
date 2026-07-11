package com.primandproper.platform.featureflags.noop

import com.primandproper.platform.featureflags.EvaluationContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Port of platform-go's `featureflags/noop/noop_test.go`. */
class NoopFeatureFlagManagerTest {
    private fun evalCtx() = EvaluationContext(targetingKey = "user-id")

    @Test
    fun `canUseFeature returns false`() =
        runTest {
            assertFalse(NoopFeatureFlagManager.canUseFeature("some-feature", evalCtx()))
        }

    @Test
    fun `getStringValue returns default`() =
        runTest {
            assertEquals("fallback", NoopFeatureFlagManager.getStringValue("some-feature", "fallback", evalCtx()))
        }

    @Test
    fun `getInt64Value returns default`() =
        runTest {
            assertEquals(42L, NoopFeatureFlagManager.getInt64Value("some-feature", 42L, evalCtx()))
        }

    @Test
    fun `getFloat64Value returns default`() =
        runTest {
            assertEquals(3.14, NoopFeatureFlagManager.getFloat64Value("some-feature", 3.14, evalCtx()), 1e-9)
        }

    @Test
    fun `getObjectValue returns default`() =
        runTest {
            val def = mapOf("k" to "v")
            assertEquals(def, NoopFeatureFlagManager.getObjectValue("some-feature", def, evalCtx()))
        }

    @Test
    fun `close does not throw`() {
        NoopFeatureFlagManager.close()
    }
}
