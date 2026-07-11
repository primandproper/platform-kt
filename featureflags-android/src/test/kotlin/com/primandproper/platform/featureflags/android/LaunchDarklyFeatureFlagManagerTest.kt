package com.primandproper.platform.featureflags.android

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
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
 * Exercises the LaunchDarkly Android backend's context/value/error mapping off-device: a fake
 * [AndroidFlagEvaluator] stands in for a live `LDClient`, so the whole adaptation matrix
 * (bool/string/int64/float/object, error-reason fallback, int64 widening/overflow, and the
 * identify-on-context-change behavior) runs in a plain JVM unit test.
 */
class LaunchDarklyFeatureFlagManagerTest {
    private fun okReason() = EvaluationReason.off()

    private fun errorReason() = EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND)

    private class FakeEvaluator(
        var bool: EvaluationDetail<Boolean> = EvaluationDetail.fromValue(false, 0, EvaluationReason.off()),
        var string: EvaluationDetail<String> = EvaluationDetail.fromValue("", 0, EvaluationReason.off()),
        var double: EvaluationDetail<Double> = EvaluationDetail.fromValue(0.0, 0, EvaluationReason.off()),
        var json: EvaluationDetail<LDValue> = EvaluationDetail.fromValue(LDValue.ofNull(), 0, EvaluationReason.off()),
    ) : AndroidFlagEvaluator {
        val identifiedContexts = mutableListOf<LDContext>()
        var closed = false

        override fun identify(context: LDContext) {
            identifiedContexts.add(context)
        }

        override fun boolDetail(
            feature: String,
            default: Boolean,
        ) = bool

        override fun stringDetail(
            feature: String,
            default: String,
        ) = string

        override fun doubleDetail(
            feature: String,
            default: Double,
        ) = double

        override fun jsonDetail(
            feature: String,
            default: LDValue,
        ) = json

        override fun close() {
            closed = true
        }
    }

    private fun evalCtx(targetingKey: String = "user123") = EvaluationContext(targetingKey = targetingKey)

    @Test
    fun `canUseFeature returns resolved value, identifies context, and observes fields`() =
        runTest {
            val fake = FakeEvaluator(bool = EvaluationDetail.fromValue(true, 0, okReason()))
            val observer = RecordingObserver()
            val mgr = LaunchDarklyFeatureFlagManager(fake, observer, initialAppliedContext = null)

            assertTrue(mgr.canUseFeature("bool-flag", evalCtx()))
            assertEquals(1, fake.identifiedContexts.size)
            assertEquals(LDContext.create("user123"), fake.identifiedContexts.single())
            observer.assertObservedOperationWithValues(
                Keys.USER_ID to "user123",
                FlagAttributes.FEATURE to "bool-flag",
                FlagAttributes.FLAG_VALUE to true,
            )
        }

    @Test
    fun `error reason falls back to default`() =
        runTest {
            val fake = FakeEvaluator(string = EvaluationDetail.fromValue("miss", 0, errorReason()))
            val mgr = LaunchDarklyFeatureFlagManager(fake, RecordingObserver(), initialAppliedContext = null)

            assertEquals("fallback", mgr.getStringValue("s", "fallback", evalCtx()))
        }

    @Test
    fun `int64 widens beyond the 32-bit range`() =
        runTest {
            val big = 5_000_000_000.0 // > Int.MAX_VALUE
            val fake = FakeEvaluator(double = EvaluationDetail.fromValue(big, 0, okReason()))
            val mgr = LaunchDarklyFeatureFlagManager(fake, RecordingObserver(), initialAppliedContext = null)

            assertEquals(5_000_000_000L, mgr.getInt64Value("n", 0L, evalCtx()))
        }

    @Test
    fun `non-representable int64 falls back to default`() =
        runTest {
            val fake = FakeEvaluator(double = EvaluationDetail.fromValue(Double.NaN, 0, okReason()))
            val mgr = LaunchDarklyFeatureFlagManager(fake, RecordingObserver(), initialAppliedContext = null)

            assertEquals(42L, mgr.getInt64Value("n", 42L, evalCtx()))
        }

    @Test
    fun `object value maps LDValue back to a Kotlin value`() =
        runTest {
            val fake = FakeEvaluator(json = EvaluationDetail.fromValue(LDValue.of("hello"), 0, okReason()))
            val mgr = LaunchDarklyFeatureFlagManager(fake, RecordingObserver(), initialAppliedContext = null)

            assertEquals("hello", mgr.getObjectValue("o", null, evalCtx()))
        }

    @Test
    fun `context is only re-identified when it changes`() =
        runTest {
            val fake = FakeEvaluator(bool = EvaluationDetail.fromValue(true, 0, okReason()))
            val mgr = LaunchDarklyFeatureFlagManager(fake, RecordingObserver(), initialAppliedContext = null)

            mgr.canUseFeature("f", evalCtx("a"))
            mgr.canUseFeature("f", evalCtx("a")) // same context — no re-identify
            mgr.canUseFeature("f", evalCtx("b")) // changed — re-identify

            assertEquals(listOf(LDContext.create("a"), LDContext.create("b")), fake.identifiedContexts)
        }

    @Test
    fun `close closes the evaluator`() =
        runTest {
            val fake = FakeEvaluator()
            LaunchDarklyFeatureFlagManager(fake, RecordingObserver(), initialAppliedContext = null).close()
            assertTrue(fake.closed)
        }

    @Test
    fun `initial applied context suppresses the first identify`() =
        runTest {
            val fake = FakeEvaluator(bool = EvaluationDetail.fromValue(true, 0, okReason()))
            val mgr = LaunchDarklyFeatureFlagManager(fake, RecordingObserver(), initialAppliedContext = LDContext.create("user123"))

            mgr.canUseFeature("f", evalCtx("user123"))
            assertFalse(fake.identifiedContexts.isNotEmpty(), "no identify expected when context matches the seeded one")
        }
}
