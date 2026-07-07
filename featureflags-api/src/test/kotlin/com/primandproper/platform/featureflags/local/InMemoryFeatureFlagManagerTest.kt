package com.primandproper.platform.featureflags.local

import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FlagAttributes
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral port of platform-go's `featureflags/launchdarkly/feature_flag_manager_test.go` against
 * the local static backend: the same bool/string/int/float/object flag matrix and default-on-miss
 * behavior, plus the observability instrumentation asserted through a `RecordingObserver`.
 */
class InMemoryFeatureFlagManagerTest {
    private fun evalCtx(targetingKey: String = "user123") = EvaluationContext(targetingKey = targetingKey)

    private fun manager(observer: RecordingObserver? = null) =
        InMemoryFeatureFlagManager(
            flags =
                mapOf(
                    "bool-flag" to true,
                    "string-flag" to "hello-world",
                    "int-flag" to 42L,
                    "float-flag" to 3.14,
                    "object-flag" to mapOf("key" to "value"),
                ),
            observer = observer,
        )

    @Test
    fun `canUseFeature returns configured value and observes fields`() =
        runTest {
            val obs = RecordingObserver()
            assertTrue(manager(obs).canUseFeature("bool-flag", evalCtx()))
            obs.assertObservedOperationWithValues(
                Keys.USER_ID to "user123",
                FlagAttributes.FEATURE to "bool-flag",
                FlagAttributes.FLAG_VALUE to true,
            )
        }

    @Test
    fun `canUseFeature returns false when flag missing`() =
        runTest {
            assertFalse(manager().canUseFeature("nonexistent", evalCtx()))
        }

    @Test
    fun `getStringValue returns configured value`() =
        runTest {
            assertEquals("hello-world", manager().getStringValue("string-flag", "fallback", evalCtx()))
        }

    @Test
    fun `getStringValue returns default when flag missing`() =
        runTest {
            assertEquals("fallback", manager().getStringValue("nonexistent", "fallback", evalCtx()))
        }

    @Test
    fun `getInt64Value returns configured value`() =
        runTest {
            assertEquals(42L, manager().getInt64Value("int-flag", 7L, evalCtx()))
        }

    @Test
    fun `getInt64Value returns default when flag missing`() =
        runTest {
            assertEquals(7L, manager().getInt64Value("nonexistent", 7L, evalCtx()))
        }

    @Test
    fun `getFloat64Value returns configured value`() =
        runTest {
            assertEquals(3.14, manager().getFloat64Value("float-flag", 1.0, evalCtx()), 1e-9)
        }

    @Test
    fun `getObjectValue returns configured value`() =
        runTest {
            assertEquals(mapOf("key" to "value"), manager().getObjectValue("object-flag", null, evalCtx()))
        }

    @Test
    fun `getObjectValue returns default when flag missing`() =
        runTest {
            val def = mapOf("k" to "v")
            assertEquals(def, manager().getObjectValue("nonexistent", def, evalCtx()))
        }
}
