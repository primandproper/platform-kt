package com.primandproper.platform.cryptography.hashing.sha256

import com.primandproper.platform.cryptography.hashing.Hasher
import java.security.MessageDigest

/** A [Hasher] backed by SHA-256. Port of platform-go's `sha256.NewSHA256Hasher`. */
public object Sha256Hasher : Hasher {
    override fun hash(content: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(content)
}
