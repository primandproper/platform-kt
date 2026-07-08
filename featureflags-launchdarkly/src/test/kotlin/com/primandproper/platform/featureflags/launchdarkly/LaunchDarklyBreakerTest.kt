package com.primandproper.platform.featureflags.launchdarkly

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
import com.primandproper.platform.circuitbreaking.CircuitState
import com.primandproper.platform.circuitbreaking.ErrCircuitBroken
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Exercises the circuit-breaker wrap added to the LaunchDarkly manager, mirroring the "with broken
 * circuit" / "with flag not found" cases of platform-go's `feature_flag_manager_test.go`. Uses a fake
 * [FlagEvaluator] (the vendor seam) so the wrap can be driven without a live client: an open breaker
 * must reject before the SDK is touched, a thrown vendor error must trip the breaker, and a flag miss
 * (an error *reason*, not a throw) must count as a success.
 */
class LaunchDarklyBreakerTest {
    private class FakeFlagEvaluator(
        private val boom: Boolean = false,
        private val reason: EvaluationReason = EvaluationReason.off(),
    ) : FlagEvaluator {
        var calls: Int = 0
            private set

        private fun <T> answer(value: T): EvaluationDetail<T> {
            calls++
            if (boom) throw RuntimeException("vendor boom")
            return EvaluationDetail.fromValue(value, 0, reason)
        }

        override fun boolDetail(
            feature: String,
            context: LDContext,
            default: Boolean,
        ): EvaluationDetail<Boolean> = answer(default)

        override fun stringDetail(
            feature: String,
            context: LDContext,
            default: String,
        ): EvaluationDetail<String> = answer(default)

        override fun intDetail(
            feature: String,
            context: LDContext,
            default: Int,
        ): EvaluationDetail<Int> = answer(default)

        override fun doubleDetail(
            feature: String,
            context: LDContext,
            default: Double,
        ): EvaluationDetail<Double> = answer(default)

        override fun jsonDetail(
            feature: String,
            context: LDContext,
            default: LDValue,
        ): EvaluationDetail<LDValue> = answer(default)

        override fun close() {}
    }

    private fun evalCtx(targetingKey: String = "user123") = EvaluationContext(targetingKey = targetingKey)

    @Test
    fun `open circuit rejects canUseFeature with ErrCircuitBroken and touches no evaluator`() =
        runTest {
            val evaluator = FakeFlagEvaluator()
            val manager = LaunchDarklyFeatureFlagManager(evaluator, RecordingObserver(), RecordingCircuitBreaker(reject = true))

            val error = assertFailsWith<Throwable> { manager.canUseFeature("some-flag", evalCtx()) }
            assertEquals(ErrCircuitBroken, error)
            assertEquals(0, evaluator.calls)
        }

    @Test
    fun `vendor error on canUseFeature trips the breaker`() =
        runTest {
            val breaker = RecordingCircuitBreaker()
            val manager = LaunchDarklyFeatureFlagManager(FakeFlagEvaluator(boom = true), RecordingObserver(), breaker)

            assertFailsWith<RuntimeException> { manager.canUseFeature("some-flag", evalCtx()) }
            assertEquals(1, breaker.failureCount)
            assertEquals(CircuitState.CLOSED, breaker.state.value)
        }

    @Test
    fun `a flag miss counts as a breaker success, not a failure`() =
        runTest {
            val breaker = RecordingCircuitBreaker()
            val reason = EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND)
            val manager = LaunchDarklyFeatureFlagManager(FakeFlagEvaluator(reason = reason), RecordingObserver(), breaker)

            assertFalse(manager.canUseFeature("nonexistent", evalCtx()))
            assertEquals(1, breaker.successCount)
            assertEquals(0, breaker.failureCount)
        }
}
