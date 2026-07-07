package com.primandproper.platform.cryptography.hashing.sha512

import com.primandproper.platform.cryptography.hashing.Hasher
import com.primandproper.platform.cryptography.hashing.toHexLower
import java.security.MessageDigest

/** Returns a [Hasher] backed by SHA-512. Port of platform-go's `sha512.NewSHA512Hasher`. */
public fun newSHA512Hasher(): Hasher = Sha512Hasher

private object Sha512Hasher : Hasher {
    override fun hash(content: String): String =
        MessageDigest.getInstance("SHA-512").digest(content.toByteArray(Charsets.UTF_8)).toHexLower()
}
