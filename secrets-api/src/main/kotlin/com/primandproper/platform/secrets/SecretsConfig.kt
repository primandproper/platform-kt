package com.primandproper.platform.secrets

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.secrets.env.EnvConfig
import com.primandproper.platform.secrets.env.EnvSecretSource
import com.primandproper.platform.secrets.noop.NoopSecretSource

/**
 * Selects and builds a [SecretSource] from configuration. Port of platform-go's
 * `secrets/config.Config` + `ProvideSecretSource`.
 *
 * Only the two in-process backends are wired here — [PROVIDER_ENV] (the default) and [PROVIDER_NOOP].
 * The three network vendor backends from the Go source are left as documented seams (see
 * [provideSecretSource]); calling into one throws [NotImplementedError] rather than silently falling
 * back, so a misconfiguration is loud.
 */
public class SecretsConfig(
    /** One of the `Provider*` constants; blank is treated as [PROVIDER_ENV]. */
    public val provider: String = PROVIDER_ENV,
    /** Configuration for the [PROVIDER_ENV] backend. */
    public val env: EnvConfig? = null,
) {
    /**
     * Validates provider selection, mirroring the Go `ValidateWithContext` `In(...)` check.
     *
     * @throws IllegalArgumentException if [provider] is not one of the known providers.
     */
    public fun validate() {
        val normalized = provider.trim().lowercase()
        require(normalized in VALID_PROVIDERS) {
            "unknown secret source provider: \"$provider\""
        }
    }

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
     * @throws IllegalArgumentException for an unknown provider.
     */
    public fun provideSecretSource(
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
    ): SecretSource =
        when (provider.trim().lowercase()) {
            "", PROVIDER_ENV -> EnvSecretSource(logger, tracerProvider)
            PROVIDER_NOOP -> NoopSecretSource
            PROVIDER_GCP -> throw NotImplementedError("TODO(gcp): GCP Secret Manager backend not ported; see SecretsConfig KDoc")
            PROVIDER_SSM -> throw NotImplementedError("TODO(ssm): AWS SSM Parameter Store backend not ported; see SecretsConfig KDoc")
            PROVIDER_KUBECTL -> throw NotImplementedError("TODO(kubectl): Kubernetes secrets backend not ported; see SecretsConfig KDoc")
            else -> throw IllegalArgumentException("unknown secret source provider: \"$provider\"")
        }

    public companion object {
        /** Environment variables (the default/primary backend). */
        public const val PROVIDER_ENV: String = "env"

        /** The no-op backend. */
        public const val PROVIDER_NOOP: String = "noop"

        /** GCP Secret Manager (TODO seam). */
        public const val PROVIDER_GCP: String = "gcp"

        /** AWS SSM Parameter Store (TODO seam). */
        public const val PROVIDER_SSM: String = "ssm"

        /** Kubernetes secrets (TODO seam). */
        public const val PROVIDER_KUBECTL: String = "kubectl"

        private val VALID_PROVIDERS =
            setOf(PROVIDER_ENV, PROVIDER_NOOP, PROVIDER_GCP, PROVIDER_SSM, PROVIDER_KUBECTL, "")
    }
}
