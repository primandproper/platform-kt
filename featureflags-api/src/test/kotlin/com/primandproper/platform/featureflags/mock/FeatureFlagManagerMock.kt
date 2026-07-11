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
 *
 * Recording is guarded by a per-mock lock; the public accessors hand back an immutable snapshot, so a
 * recorder on one thread can't trip a reader iterating the calls on another.
 */
public class FeatureFlagManagerMock : FeatureFlagManager {
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

    private val lock = Any()

    private val _canUseFeatureCalls = mutableListOf<Call>()
    private val _getStringValueCalls = mutableListOf<Call>()
    private val _getInt64ValueCalls = mutableListOf<Call>()
    private val _getFloat64ValueCalls = mutableListOf<Call>()
    private val _getObjectValueCalls = mutableListOf<Call>()
    private var _closeCalls = 0

    public val canUseFeatureCalls: List<Call> get() = synchronized(lock) { _canUseFeatureCalls.toList() }
    public val getStringValueCalls: List<Call> get() = synchronized(lock) { _getStringValueCalls.toList() }
    public val getInt64ValueCalls: List<Call> get() = synchronized(lock) { _getInt64ValueCalls.toList() }
    public val getFloat64ValueCalls: List<Call> get() = synchronized(lock) { _getFloat64ValueCalls.toList() }
    public val getObjectValueCalls: List<Call> get() = synchronized(lock) { _getObjectValueCalls.toList() }
    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }

    override suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean {
        synchronized(lock) { _canUseFeatureCalls += Call(feature, false, evalCtx) }
        return canUseFeatureFn(feature, evalCtx)
    }

    override suspend fun getStringValue(
        feature: String,
        defaultValue: String,
        evalCtx: EvaluationContext,
    ): String {
        synchronized(lock) { _getStringValueCalls += Call(feature, defaultValue, evalCtx) }
        return getStringValueFn(feature, defaultValue, evalCtx)
    }

    override suspend fun getInt64Value(
        feature: String,
        defaultValue: Long,
        evalCtx: EvaluationContext,
    ): Long {
        synchronized(lock) { _getInt64ValueCalls += Call(feature, defaultValue, evalCtx) }
        return getInt64ValueFn(feature, defaultValue, evalCtx)
    }

    override suspend fun getFloat64Value(
        feature: String,
        defaultValue: Double,
        evalCtx: EvaluationContext,
    ): Double {
        synchronized(lock) { _getFloat64ValueCalls += Call(feature, defaultValue, evalCtx) }
        return getFloat64ValueFn(feature, defaultValue, evalCtx)
    }

    override suspend fun getObjectValue(
        feature: String,
        defaultValue: Any?,
        evalCtx: EvaluationContext,
    ): Any? {
        synchronized(lock) { _getObjectValueCalls += Call(feature, defaultValue, evalCtx) }
        return getObjectValueFn(feature, defaultValue, evalCtx)
    }

    override fun close() {
        synchronized(lock) { _closeCalls++ }
        closeFn()
    }
}
