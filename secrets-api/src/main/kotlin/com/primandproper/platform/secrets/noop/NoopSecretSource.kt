package com.primandproper.platform.secrets.noop

import com.primandproper.platform.secrets.SecretSource

/**
 * A no-op [SecretSource]: returns the empty string for every key and never throws. Port of
 * platform-go's `secrets/noop.secretSource` — a safe default for wiring, and for tests that don't
 * care about real secret values.
 */
public object NoopSecretSource : SecretSource {
    override suspend fun getSecret(name: String): String = ""

    override fun close() {
        // no-op
    }
}
