package com.primandproper.platform.featureflags.android

import android.app.Application
import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.LDValueType
import com.launchdarkly.sdk.android.LDClient
import com.launchdarkly.sdk.android.LDConfig
import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FeatureFlagManager
import com.primandproper.platform.featureflags.FlagAttributes
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val LD_SERVICE_NAME = "launchdarkly_android_feature_flag_manager"

/** The default init wait, in seconds, for [launchDarklyFeatureFlagManager]. */
public const val DEFAULT_LD_INIT_TIMEOUT_SECONDS: Int = 5

/**
 * Raised (and recorded on the observability span) when LaunchDarkly reports an error reason for a
 * flag evaluation — most commonly `FLAG_NOT_FOUND`. The caller still receives the supplied default
 * value; this mirrors the server backend's [com.primandproper.platform.featureflags.launchdarkly]
 * behavior so both crossings surface a miss the same way.
 */
public class FeatureFlagEvaluationException(
    feature: String,
    reason: EvaluationReason,
) : RuntimeException("error evaluating flag \"$feature\": ${reason.errorKind ?: reason.kind}")

/**
 * Raised (and recorded on the span) when an Int64 flag resolves to a numeric value that cannot be
 * represented as a [Long] — a non-finite value, or one outside the Long range. The caller receives the
 * supplied default rather than a silently wrapped number.
 */
public class FeatureFlagInt64RangeException(
    feature: String,
    value: Double,
) : RuntimeException("flag \"$feature\" resolved to $value, which is not a representable Int64")

/**
 * The seam through which the manager evaluates against LaunchDarkly. Production wires it to a live
 * Android [LDClient] (see [LDClientEvaluator]); tests supply a fake that records the applied context
 * and returns canned [EvaluationDetail]s, so the context/int64/error-reason mapping is exercised
 * off-device without a live client.
 *
 * Unlike the LaunchDarkly *server* SDK, the Android client evaluates against a single *current*
 * context rather than taking one per call, so this seam separates [identify] (apply a context) from
 * the context-free `*Detail` evaluations.
 */
internal interface AndroidFlagEvaluator {
    /** Applies [context] as the client's current evaluation context (blocking). */
    fun identify(context: LDContext)

    fun boolDetail(
        feature: String,
        default: Boolean,
    ): EvaluationDetail<Boolean>

    fun stringDetail(
        feature: String,
        default: String,
    ): EvaluationDetail<String>

    fun doubleDetail(
        feature: String,
        default: Double,
    ): EvaluationDetail<Double>

    fun jsonDetail(
        feature: String,
        default: LDValue,
    ): EvaluationDetail<LDValue>

    fun close()
}

/** The production [AndroidFlagEvaluator]: a thin adapter over a live Android [LDClient]. */
private class LDClientEvaluator(private val client: LDClient) : AndroidFlagEvaluator {
    override fun identify(context: LDContext) {
        // identify returns a Future; block for it so a subsequent evaluation sees the new context.
        client.identify(context).get()
    }

    override fun boolDetail(
        feature: String,
        default: Boolean,
    ): EvaluationDetail<Boolean> = client.boolVariationDetail(feature, default)

    override fun stringDetail(
        feature: String,
        default: String,
    ): EvaluationDetail<String> = client.stringVariationDetail(feature, default)

    override fun doubleDetail(
        feature: String,
        default: Double,
    ): EvaluationDetail<Double> = client.doubleVariationDetail(feature, default)

    override fun jsonDetail(
        feature: String,
        default: LDValue,
    ): EvaluationDetail<LDValue> = client.jsonValueVariationDetail(feature, default)

    override fun close() {
        client.close()
    }
}

/**
 * A [FeatureFlagManager] backed by the LaunchDarkly Android client SDK — the on-device counterpart of
 * the server `:featureflags-launchdarkly` backend, and the sibling of this module's local
 * [DataStoreFeatureFlagManager]. Every evaluation opens a span recording the subject, feature key,
 * default, and resolved value; an error reason from the SDK is recorded and the caller gets the
 * supplied default.
 *
 * The Android client evaluates against a single *current* context, so before each evaluation the
 * manager applies the call's [EvaluationContext] via `identify` — but only when it differs from the
 * one already applied (LaunchDarkly's Android client is typically driven for one on-device user, so
 * the context rarely changes between calls). The identify + evaluation are performed together under a
 * mutex so concurrent calls with different contexts cannot interleave and read each other's context.
 *
 * Construct it from a live client with [launchDarklyFeatureFlagManager]; the internal
 * evaluator-seam constructor is for tests.
 */
public class LaunchDarklyFeatureFlagManager internal constructor(
    private val evaluator: AndroidFlagEvaluator,
    observer: Observer = noopObserver(LD_SERVICE_NAME),
    initialAppliedContext: LDContext?,
) : FeatureFlagManager {
    private val o11y: Observer = observer
    private val contextMutex = Mutex()
    private var appliedContext: LDContext? = initialAppliedContext

    /** Applies [evalCtx]'s context (if changed) and runs [block] against the client, atomically. */
    private suspend fun <T> evaluating(
        evalCtx: EvaluationContext,
        block: () -> T,
    ): T {
        val ctx = toLDContext(evalCtx)
        return contextMutex.withLock {
            withContext(Dispatchers.IO) {
                if (ctx != appliedContext) {
                    evaluator.identify(ctx)
                    appliedContext = ctx
                }
                block()
            }
        }
    }

    override suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean =
        o11y.span("canUseFeature") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val detail = evaluating(evalCtx) { evaluator.boolDetail(feature, false) }
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
            val detail = evaluating(evalCtx) { evaluator.stringDetail(feature, defaultValue) }
            if (detail.reason.isError()) {
                acknowledge(FeatureFlagEvaluationException(feature, detail.reason), "checking feature flag string variation")
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
            // LaunchDarkly stores numeric flags as JSON doubles and intVariationDetail funnels through
            // Int, silently wrapping values outside the 32-bit range. Widen through doubleVariationDetail
            // then narrow back to Long, so the full Int64 range survives (mirrors the server backend).
            val detail = evaluating(evalCtx) { evaluator.doubleDetail(feature, defaultValue.toDouble()) }
            if (detail.reason.isError()) {
                acknowledge(FeatureFlagEvaluationException(feature, detail.reason), "checking feature flag int variation")
                return@span defaultValue
            }
            val result = detail.value.toInt64OrNull()
            if (result == null) {
                acknowledge(FeatureFlagInt64RangeException(feature, detail.value), "checking feature flag int variation")
                return@span defaultValue
            }
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
            val detail = evaluating(evalCtx) { evaluator.doubleDetail(feature, defaultValue) }
            if (detail.reason.isError()) {
                acknowledge(FeatureFlagEvaluationException(feature, detail.reason), "checking feature flag float variation")
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
            val detail = evaluating(evalCtx) { evaluator.jsonDetail(feature, toLDValue(defaultValue)) }
            if (detail.reason.isError()) {
                acknowledge(FeatureFlagEvaluationException(feature, detail.reason), "checking feature flag object variation")
                return@span defaultValue
            }
            ldValueToAny(detail.value)
        }

    override fun close() {
        evaluator.close()
    }
}

/**
 * Builds a [LaunchDarklyFeatureFlagManager] over a live Android [LDClient], initialized for
 * [evaluationContext] (the app's current user). [configure] is applied to the SDK config builder
 * before the client is created.
 *
 * @param application the host Application (the Android client requires it).
 * @param mobileKey the LaunchDarkly *mobile* key; blank throws.
 * @param evaluationContext the initial context the client is identified with.
 * @param initTimeoutSeconds how long [LDClient.init] waits for the first flag sync.
 * @throws IllegalArgumentException when [mobileKey] is blank.
 */
public fun launchDarklyFeatureFlagManager(
    application: Application,
    mobileKey: String,
    evaluationContext: EvaluationContext,
    observer: Observer = noopObserver(LD_SERVICE_NAME),
    initTimeoutSeconds: Int = DEFAULT_LD_INIT_TIMEOUT_SECONDS,
    configure: LDConfig.Builder.() -> Unit = {},
): LaunchDarklyFeatureFlagManager {
    require(mobileKey.isNotBlank()) { "missing LaunchDarkly mobile key" }
    val initialContext = toLDContext(evaluationContext)
    val config =
        LDConfig.Builder(LDConfig.Builder.AutoEnvAttributes.Enabled)
            .mobileKey(mobileKey)
            .apply(configure)
            .build()
    val client = LDClient.init(application, config, initialContext, initTimeoutSeconds)
    return LaunchDarklyFeatureFlagManager(LDClientEvaluator(client), observer, initialContext)
}

private fun EvaluationReason.isError(): Boolean = kind == EvaluationReason.Kind.ERROR

/**
 * Narrows a LaunchDarkly numeric flag value (always a JSON double) back to a [Long] without the
 * silent Int wraparound of `intVariationDetail`. Returns `null` when the value is not a finite number
 * within the Long range. `Long.MAX_VALUE` is not exactly representable as a double (rounds to 2^63),
 * so the upper bound is strict.
 */
internal fun Double.toInt64OrNull(): Long? =
    if (isFinite() && this >= Long.MIN_VALUE.toDouble() && this < Long.MAX_VALUE.toDouble()) {
        toLong()
    } else {
        null
    }

/** Converts a platform-owned [EvaluationContext] into a LaunchDarkly [LDContext]. */
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
