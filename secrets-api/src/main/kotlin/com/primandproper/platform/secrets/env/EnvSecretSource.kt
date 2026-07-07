package com.primandproper.platform.secrets.env

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.primandproper.platform.secrets.SecretNotFoundException
import com.primandproper.platform.secrets.SecretSource

/**
 * The primary, local [SecretSource]: reads secrets straight from the process environment. Port of
 * platform-go's `secrets/env.envSecretSource`.
 *
 * Every retrieval opens a span (mirroring `g.o11y.Begin(ctx)` / `op.Set("secret_key", name)`) and
 * records **only the lookup key** — never the value. That single restraint is the whole security
 * property: because the value is never passed to [com.primandproper.platform.observability.Operation.set],
 * it cannot land on a span attribute or a log field, so there is nothing to redact after the fact.
 *
 * @constructor Internal seam taking an already-built [Observer] and env lookup, so a test can inject a
 *   `RecordingObserver` and a deterministic map without mutating the real process environment (the
 *   JVM has no portable `setenv`, unlike Go's `os.Setenv`).
 */
public class EnvSecretSource internal constructor(
    private val o11y: Observer,
    private val lookup: (String) -> String?,
) : SecretSource {
    /**
     * @param logger optional root logger; defaults to a noop logger.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     * @param lookup the environment reader; defaults to [System.getenv]. Overridable for tests.
     */
    public constructor(
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
        lookup: (String) -> String? = { System.getenv(it) },
    ) : this(Observer(NAME, logger, tracerProvider), lookup)

    override suspend fun getSecret(name: String): String =
        o11y.span("GetSecret") {
            // NOTE: only the secret's lookup key is observed, never its value.
            set("secret_key", name)
            lookup(name) ?: throw error(
                SecretNotFoundException(name),
                "environment variable not set",
            )
        }

    override fun close() {
        o11y.logger.debug("closing env secret source")
    }

    public companion object {
        /** Component name applied to the logger and tracer, matching platform-go's `env` `name`. */
        public const val NAME: String = "env_secret_source"
    }
}
