package com.primandproper.platform.featureflags.launchdarkly

import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.LDValueType
import com.launchdarkly.sdk.server.LDClient
import com.launchdarkly.sdk.server.LDConfig
import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FeatureFlagManager
import com.primandproper.platform.featureflags.FlagAttributes
import com.primandproper.platform.featureflags.config.LaunchDarklyConfig
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val SERVICE_NAME: String = "launchdarkly_feature_flag_manager"

/**
 * Raised (and recorded on the observability span) when LaunchDarkly reports an error reason for a
 * flag evaluation — most commonly `FLAG_NOT_FOUND`. Mirrors the error platform-go returns from the
 * OpenFeature client so the caller still receives the supplied default value.
 */
public class FeatureFlagEvaluationException(
    feature: String,
    reason: EvaluationReason,
) : RuntimeException("error evaluating flag \"$feature\": ${reason.errorKind ?: reason.kind}")

/**
 * A [FeatureFlagManager] backed by the LaunchDarkly Java server SDK. This is the only place the
 * provider crosses the boundary between the platform-owned [EvaluationContext]/values and the
 * LaunchDarkly [LDContext]/[LDValue] types. Every evaluation opens a span and records the subject,
 * feature key, default, and resolved value; an error reason from the SDK is recorded and the caller
 * gets the supplied default — matching the platform-go behavior.
 *
 * Construct it from a live client, or use [launchDarklyFeatureFlagManager] to build a client from
 * config. Tests inject a client fed by the SDK's offline `TestData` data source, so the
 * mapping/adaptation logic is verified without a network connection.
 *
 * TODO(posthog): the PostHog server backend from platform-go is a documented seam — it belongs in a
 * separate `:featureflags-posthog` module wrapping the PostHog SDK behind this same interface.
 */
public class LaunchDarklyFeatureFlagManager(
    private val client: LDClient,
    observer: Observer? = null,
) : FeatureFlagManager {
    private val o11y: Observer = observer ?: noopObserver(SERVICE_NAME)

    override suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean =
        o11y.span("canUseFeature") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val detail = withContext(Dispatchers.IO) { client.boolVariationDetail(feature, toLDContext(evalCtx), false) }
            if (detail.reason.isError()) {
                acknowledge(FeatureFlagEvaluationException(feature, detail.reason), "checking feature flag variation")
                return@span false
            }
            val result = detail.value
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
            val detail =
                withContext(Dispatchers.IO) {
                    client.stringVariationDetail(feature, toLDContext(evalCtx), defaultValue)
                }
            if (detail.reason.isError()) {
                acknowledge(
                    FeatureFlagEvaluationException(feature, detail.reason),
                    "checking feature flag string variation",
                )
                return@span defaultValue
            }
            val result = detail.value
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
            val detail =
                withContext(Dispatchers.IO) {
                    client.intVariationDetail(feature, toLDContext(evalCtx), defaultValue.toInt())
                }
            if (detail.reason.isError()) {
                acknowledge(FeatureFlagEvaluationException(feature, detail.reason), "checking feature flag int variation")
                return@span defaultValue
            }
            val result = detail.value.toLong()
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
            val detail =
                withContext(Dispatchers.IO) {
                    client.doubleVariationDetail(feature, toLDContext(evalCtx), defaultValue)
                }
            if (detail.reason.isError()) {
                acknowledge(
                    FeatureFlagEvaluationException(feature, detail.reason),
                    "checking feature flag float variation",
                )
                return@span defaultValue
            }
            val result = detail.value
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
            val detail =
                withContext(Dispatchers.IO) {
                    client.jsonValueVariationDetail(feature, toLDContext(evalCtx), toLDValue(defaultValue))
                }
            if (detail.reason.isError()) {
                acknowledge(
                    FeatureFlagEvaluationException(feature, detail.reason),
                    "checking feature flag object variation",
                )
                return@span defaultValue
            }
            ldValueToAny(detail.value)
        }

    override fun close() {
        client.close()
    }
}

/**
 * Builds a [LaunchDarklyFeatureFlagManager] from [config], constructing the underlying [LDClient].
 * [configure] is applied to the SDK config builder before the client is created — this is where a
 * test swaps in an offline `TestData` data source. Mirrors platform-go's `NewFeatureFlagManager`
 * plus its variadic config-modifier hook.
 *
 * @throws IllegalArgumentException when the SDK key is blank.
 */
public fun launchDarklyFeatureFlagManager(
    config: LaunchDarklyConfig,
    observer: Observer? = null,
    configure: LDConfig.Builder.() -> Unit = {},
): LaunchDarklyFeatureFlagManager {
    require(config.sdkKey.isNotBlank()) { "missing SDK key" }
    val ldConfig = LDConfig.Builder().apply(configure).build()
    return LaunchDarklyFeatureFlagManager(LDClient(config.sdkKey, ldConfig), observer)
}

private fun EvaluationReason.isError(): Boolean = kind == EvaluationReason.Kind.ERROR

/**
 * Converts a platform-owned [EvaluationContext] into a LaunchDarkly [LDContext]. The only crossing
 * from the repo type to the vendor type on the write side.
 */
internal fun toLDContext(evalCtx: EvaluationContext): LDContext {
    val builder = LDContext.builder(evalCtx.targetingKey)
    for ((key, value) in evalCtx.attributes) {
        builder.set(key, toLDValue(value))
    }
    return builder.build()
}

/** Widens an arbitrary Kotlin value into an [LDValue], the type LaunchDarkly evaluates against. */
internal fun toLDValue(value: Any?): LDValue =
    when (value) {
        null -> LDValue.ofNull()
        is LDValue -> value
        is Boolean -> LDValue.of(value)
        is Int -> LDValue.of(value)
        is Long -> LDValue.of(value)
        is Short -> LDValue.of(value.toInt())
        is Byte -> LDValue.of(value.toInt())
        is Double -> LDValue.of(value)
        is Float -> LDValue.of(value.toDouble())
        is String -> LDValue.of(value)
        else -> LDValue.of(value.toString())
    }

/** Narrows an [LDValue] back into a plain Kotlin value for the object-flag return path. */
internal fun ldValueToAny(value: LDValue): Any? =
    when (value.type) {
        LDValueType.NULL -> null
        LDValueType.BOOLEAN -> value.booleanValue()
        LDValueType.NUMBER -> if (value.isInt) value.intValue().toLong() else value.doubleValue()
        LDValueType.STRING -> value.stringValue()
        LDValueType.ARRAY -> value.values().map { ldValueToAny(it) }
        LDValueType.OBJECT -> value.keys().associateWith { ldValueToAny(value.get(it)) }
        else -> null
    }
