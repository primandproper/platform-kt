package com.primandproper.platform.featureflags.noop

import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FeatureFlagManager

/**
 * A no-op [FeatureFlagManager] that always returns the supplied default values (or `false` for the
 * boolean variant). The default a caller gets when no provider is configured. Port of
 * platform-go's `featureflags/noop`.
 */
public object NoopFeatureFlagManager : FeatureFlagManager {
    override suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean = false

    override suspend fun getStringValue(
        feature: String,
        defaultValue: String,
        evalCtx: EvaluationContext,
    ): String = defaultValue

    override suspend fun getInt64Value(
        feature: String,
        defaultValue: Long,
        evalCtx: EvaluationContext,
    ): Long = defaultValue

    override suspend fun getFloat64Value(
        feature: String,
        defaultValue: Double,
        evalCtx: EvaluationContext,
    ): Double = defaultValue

    override suspend fun getObjectValue(
        feature: String,
        defaultValue: Any?,
        evalCtx: EvaluationContext,
    ): Any? = defaultValue

    override fun close() {
        // no resources to release
    }
}
