package com.primandproper.platform.featureflags.mock

import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FeatureFlagManager

/**
 * A programmable [FeatureFlagManager] test double. Each method delegates to a settable lambda and
 * records its call arguments, so a test wires up the responses it needs and later asserts which
 * evaluations happened. Kotlin analog of platform-go's moq-generated `FeatureFlagManagerMock`.
 *
 * Every `*Fn` defaults to returning the caller-supplied default (or `false` for the boolean
 * variant), so an unconfigured mock behaves like the noop manager.
 */
public class MockFeatureFlagManager : FeatureFlagManager {
    /** A single recorded evaluation call. */
    public data class Call(
        val feature: String,
        val defaultValue: Any?,
        val evalCtx: EvaluationContext,
    )

    public var canUseFeatureFn: (feature: String, evalCtx: EvaluationContext) -> Boolean = { _, _ -> false }
    public var getStringValueFn: (feature: String, defaultValue: String, evalCtx: EvaluationContext) -> String =
        { _, d, _ -> d }
    public var getInt64ValueFn: (feature: String, defaultValue: Long, evalCtx: EvaluationContext) -> Long =
        { _, d, _ -> d }
    public var getFloat64ValueFn: (feature: String, defaultValue: Double, evalCtx: EvaluationContext) -> Double =
        { _, d, _ -> d }
    public var getObjectValueFn: (feature: String, defaultValue: Any?, evalCtx: EvaluationContext) -> Any? =
        { _, d, _ -> d }
    public var closeFn: () -> Unit = {}

    public val canUseFeatureCalls: MutableList<Call> = mutableListOf()
    public val getStringValueCalls: MutableList<Call> = mutableListOf()
    public val getInt64ValueCalls: MutableList<Call> = mutableListOf()
    public val getFloat64ValueCalls: MutableList<Call> = mutableListOf()
    public val getObjectValueCalls: MutableList<Call> = mutableListOf()
    public var closeCalls: Int = 0
        private set

    override suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean {
        canUseFeatureCalls += Call(feature, false, evalCtx)
        return canUseFeatureFn(feature, evalCtx)
    }

    override suspend fun getStringValue(
        feature: String,
        defaultValue: String,
        evalCtx: EvaluationContext,
    ): String {
        getStringValueCalls += Call(feature, defaultValue, evalCtx)
        return getStringValueFn(feature, defaultValue, evalCtx)
    }

    override suspend fun getInt64Value(
        feature: String,
        defaultValue: Long,
        evalCtx: EvaluationContext,
    ): Long {
        getInt64ValueCalls += Call(feature, defaultValue, evalCtx)
        return getInt64ValueFn(feature, defaultValue, evalCtx)
    }

    override suspend fun getFloat64Value(
        feature: String,
        defaultValue: Double,
        evalCtx: EvaluationContext,
    ): Double {
        getFloat64ValueCalls += Call(feature, defaultValue, evalCtx)
        return getFloat64ValueFn(feature, defaultValue, evalCtx)
    }

    override suspend fun getObjectValue(
        feature: String,
        defaultValue: Any?,
        evalCtx: EvaluationContext,
    ): Any? {
        getObjectValueCalls += Call(feature, defaultValue, evalCtx)
        return getObjectValueFn(feature, defaultValue, evalCtx)
    }

    override fun close() {
        closeCalls++
        closeFn()
    }
}
