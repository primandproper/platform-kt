package com.primandproper.platform.secrets

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.secrets.env.EnvSecretSource
import com.primandproper.platform.secrets.noop.NoopSecretSource

/**
 * The supported secret-source providers. Port of platform-go's `secrets/config.Provider*` constants,
 * modelled as an enum so an unknown provider is rejected the way Go's `validation.In(...)` rejects it.
 * [value] is the wire/string form validated against configuration.
 *
 * Only the two in-process backends are wired here — [ENV] (the default) and [NOOP]. The three network
 * vendor backends from the Go source are left as documented seams (see
 * [SecretsConfig.SecretSource]); the enum still lists them so a config naming, say, `"gcp"`
 * resolves to a known provider even before its backend lands, matching Go's `validation.In(...)`.
 */
public enum class SecretProvider(
    public val value: String,
) {
    /** Environment variables (the default/primary backend). */
    ENV("env"),

    /** The no-op backend. */
    NOOP("noop"),

    /** GCP Secret Manager (TODO seam). */
    GCP("gcp"),

    /** AWS SSM Parameter Store (TODO seam). */
    SSM("ssm"),

    /** Kubernetes secrets (TODO seam). */
    KUBECTL("kubectl"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — the parse edge where a raw config string becomes the typed enum.
         * A blank value resolves to `null` here; the module's blank→[ENV] default is applied by
         * [SecretsConfig.providerFromValue].
         */
        public fun fromValue(value: String): SecretProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * Selects and builds a [SecretSource] from configuration. Port of platform-go's
 * `secrets/config.Config` + `ProvideSecretSource`.
 *
 * [provider] is a typed [SecretProvider], resolved from its string form once at the parse edge
 * ([SecretsConfig.providerFromValue] / [SecretProvider.fromValue]); the default is [SecretProvider.ENV],
 * preserving Go's "blank provider means env" behavior. [SecretSource] therefore dispatches over
 * an exhaustive `when` with no unknown arm.
 */
public class SecretsConfig(
    /** The selected backend; defaults to [SecretProvider.ENV]. */
    public val provider: SecretProvider = SecretProvider.ENV,
) {
    /**
     * Returns the configured [SecretSource].
     *
     * TODO(gcp): wire GCP Secret Manager — Go's `gcp.NewGCPSecretSource` over
     *   `cloud.google.com/go/secretmanager/apiv1` (`SecretVersionAccessor.AccessSecretVersion`).
     * TODO(ssm): wire AWS SSM Parameter Store — Go's `ssm.NewSSMSecretSource` over
     *   `aws-sdk-go-v2/service/ssm` (`GetParameterAPI.GetParameter`).
     * TODO(kubectl): wire Kubernetes Secrets — Go's `kubectl.NewKubectlSecretSource` over
     *   `k8s.io/client-go` (`SecretGetter.Get`).
     *
     * @throws NotImplementedError for the un-ported vendor providers.
     */
    public fun SecretSource(
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ): SecretSource =
        when (provider) {
            SecretProvider.ENV -> EnvSecretSource(logger, tracerProvider)
            SecretProvider.NOOP -> NoopSecretSource
            SecretProvider.GCP -> throw NotImplementedError("TODO(gcp): GCP Secret Manager backend not ported; see SecretsConfig KDoc")
            SecretProvider.SSM -> throw NotImplementedError("TODO(ssm): AWS SSM Parameter Store backend not ported; see SecretsConfig KDoc")
            SecretProvider.KUBECTL -> throw NotImplementedError(
                "TODO(kubectl): Kubernetes secrets backend not ported; see SecretsConfig KDoc",
            )
        }

    public companion object {
        /**
         * The parse edge: resolves a raw provider string (e.g. from an env var) to a [SecretProvider],
         * treating a blank value as [SecretProvider.ENV] (the default backend) and rejecting an unknown
         * name loudly rather than silently degrading. Mirrors Go's `ValidateWithContext` `In(...)` check.
         *
         * @throws IllegalArgumentException for a non-blank unknown provider.
         */
        public fun providerFromValue(value: String): SecretProvider =
            if (value.isBlank()) {
                SecretProvider.ENV
            } else {
                SecretProvider.fromValue(value)
                    ?: throw IllegalArgumentException("unknown secret source provider: \"$value\"")
            }
    }
}
