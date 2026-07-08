package com.primandproper.platform.featureflags

/**
 * Carries targeting information for a single flag evaluation. [targetingKey] is the primary subject
 * identifier — typically a user ID, but any stable string a provider's targeting rules can match
 * against. [attributes] carry arbitrary additional signals (tenant, plan tier, country, beta cohort,
 * region, …) that provider rules can target on.
 *
 * This type is intentionally repo-owned rather than aliasing a vendor SDK's context type: it keeps
 * the vendor import out of caller code, lets the noop / mock / in-memory implementations satisfy the
 * signature without importing a vendor, and leaves room to swap providers later. Each provider
 * converts to its own representation internally. Port of platform-go's `EvaluationContext`.
 */
public data class EvaluationContext(
    public val targetingKey: String,
    public val attributes: Map<String, Any?> = emptyMap(),
)

/**
 * Standard span/log attribute keys attached during a flag evaluation, so a value is named the same
 * way wherever it is recorded. Mirrors the string keys the platform-go providers set.
 */
public object FlagAttributes {
    /** The flag key being evaluated. */
    public const val FEATURE: String = "feature"

    /** The resolved flag value. */
    public const val FLAG_VALUE: String = "flag.value"

    /** The caller-supplied fallback value. */
    public const val FLAG_DEFAULT: String = "flag.default"
}

/**
 * Evaluates feature flags. Implementations must be safe for concurrent use. Port of platform-go's
 * `FeatureFlagManager` interface — the Go `context.Context` argument becomes Kotlin's `suspend`, and
 * the `(value, error)` return becomes a value that falls back to the supplied default on failure.
 */
public interface FeatureFlagManager : AutoCloseable {
    /** Evaluates a boolean flag. Returns `false` on error. */
    public suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean

    /** Evaluates a string-typed flag, returning [defaultValue] on error. */
    public suspend fun getStringValue(
        feature: String,
        defaultValue: String,
        evalCtx: EvaluationContext,
    ): String

    /** Evaluates an int64-typed flag, returning [defaultValue] on error. */
    public suspend fun getInt64Value(
        feature: String,
        defaultValue: Long,
        evalCtx: EvaluationContext,
    ): Long

    /** Evaluates a float64-typed flag, returning [defaultValue] on error. */
    public suspend fun getFloat64Value(
        feature: String,
        defaultValue: Double,
        evalCtx: EvaluationContext,
    ): Double

    /**
     * Evaluates an object-typed (JSON) flag, returning [defaultValue] on error. The concrete type of
     * the returned value is provider-specific — callers typically cast it or serialize it back into
     * a known type.
     */
    public suspend fun getObjectValue(
        feature: String,
        defaultValue: Any?,
        evalCtx: EvaluationContext,
    ): Any?

    /** Releases any backend resources held by this manager. */
    override fun close()
}
