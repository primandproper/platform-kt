package com.primandproper.platform.secrets

/**
 * Provides read access to secrets by name. Port of platform-go's `secrets.SecretSource`.
 *
 * Go returns `(string, error)`; the network-backed backends (GCP, SSM, kubectl) genuinely do I/O, so
 * the retrieval is modelled as a `suspend` function here — the same coroutine-native choice
 * `httpclient`'s `HttpClient.execute` makes — while the in-process backends ([env] and the Android
 * keystore store) simply never suspend.
 *
 * ## Secret values are never observed
 * Implementations MUST NOT record a secret's *value* on a span or in a log line — only the lookup
 * *key* may be observed. This is the secrets analog of `httpclient`'s header redaction: the guarantee
 * is enforced by never handing the value to the observability pillars in the first place. See the
 * `NOTE` in [com.primandproper.platform.secrets.env.EnvSecretSource].
 */
public interface SecretSource {
    /**
     * Returns the secret stored under [name].
     *
     * @throws SecretNotFoundException when no secret exists for [name], so a missing secret is
     *   distinguishable from one whose value is legitimately the empty string.
     */
    public suspend fun getSecret(name: String): String

    /** Releases any resources (network clients, handles). A no-op for the in-process backends. */
    public fun close()
}

/**
 * Thrown when a requested secret does not exist. Port of platform-go's sentinel `ErrSecretNotFound`;
 * the missing [key] is carried for diagnostics but the (absent) value never is.
 */
public class SecretNotFoundException(
    public val key: String,
    message: String = "secret not found",
) : RuntimeException(message)
