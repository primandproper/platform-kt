package com.primandproper.platform.cryptography.hashing.sha256

import com.primandproper.platform.cryptography.hashing.Hasher
import com.primandproper.platform.cryptography.hashing.toHexLower
import java.security.MessageDigest

/** Returns a [Hasher] backed by SHA-256. Port of platform-go's `sha256.NewSHA256Hasher`. */
public fun newSHA256Hasher(): Hasher = Sha256Hasher

private object Sha256Hasher : Hasher {
    override fun hash(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8)).toHexLower()
}
