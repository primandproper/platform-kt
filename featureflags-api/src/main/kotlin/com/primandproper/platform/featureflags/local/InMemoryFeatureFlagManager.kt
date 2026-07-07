package com.primandproper.platform.featureflags.local

import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FeatureFlagManager
import com.primandproper.platform.featureflags.FlagAttributes
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span

private const val SERVICE_NAME = "in_memory_feature_flag_manager"

/**
 * A simple static, in-memory [FeatureFlagManager] backed by a fixed map of flag key → value. Useful
 * for tests, local development, and as the "no vendor" backend. Values are coerced to the requested
 * type; a missing flag (or a type mismatch) yields the caller-supplied default. Reads are lock-free
 * over an immutable snapshot, so it is safe for concurrent use.
 *
 * Every evaluation opens an observability span named for the operation and records the subject, the
 * feature key, the default, and the resolved value — the same instrumentation the vendor backends
 * emit — so a unit can be asserted against a `RecordingObserver`.
 */
public class InMemoryFeatureFlagManager(
    flags: Map<String, Any?> = emptyMap(),
    observer: Observer? = null,
) : FeatureFlagManager {
    private val flags: Map<String, Any?> = flags.toMap()
    private val o11y: Observer = observer ?: noopObserver(SERVICE_NAME)

    override suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean =
        o11y.span("canUseFeature") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = flags[feature] as? Boolean ?: false
            set(FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getStringValue(
        feature: String,
        defaultValue: String,
        evalCtx: EvaluationContext,
    ): String =
        o11y.span("getStringValue") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = flags[feature] as? String ?: defaultValue
            set(FlagAttributes.FLAG_DEFAULT to defaultValue, FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getInt64Value(
        feature: String,
        defaultValue: Long,
        evalCtx: EvaluationContext,
    ): Long =
        o11y.span("getInt64Value") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = (flags[feature] as? Number)?.toLong() ?: defaultValue
            set(FlagAttributes.FLAG_DEFAULT to defaultValue, FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getFloat64Value(
        feature: String,
        defaultValue: Double,
        evalCtx: EvaluationContext,
    ): Double =
        o11y.span("getFloat64Value") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = (flags[feature] as? Number)?.toDouble() ?: defaultValue
            set(FlagAttributes.FLAG_DEFAULT to defaultValue, FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getObjectValue(
        feature: String,
        defaultValue: Any?,
        evalCtx: EvaluationContext,
    ): Any? =
        o11y.span("getObjectValue") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = if (flags.containsKey(feature)) flags[feature] else defaultValue
            result
        }

    override fun close() {
        // no resources to release
    }
}
