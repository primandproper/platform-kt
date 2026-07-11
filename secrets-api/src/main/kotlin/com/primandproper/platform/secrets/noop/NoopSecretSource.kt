package com.primandproper.platform.secrets.noop

import com.primandproper.platform.secrets.SecretNotFoundException
import com.primandproper.platform.secrets.SecretSource

/**
 * A no-op [SecretSource] that holds no secrets: every lookup throws [SecretNotFoundException],
 * honoring the [SecretSource.getSecret] contract (a missing secret is distinguishable from one whose
 * value is legitimately empty). Unlike platform-go's `secrets/noop.secretSource` (which returns `""`),
 * this fails loudly so an unconfigured secret source surfaces at the first lookup rather than silently
 * handing back empty values that read as real secrets.
 */
public object NoopSecretSource : SecretSource {
    override suspend fun getSecret(name: String): String = throw SecretNotFoundException(name)

    override suspend fun close() {
        // no-op
    }
}
