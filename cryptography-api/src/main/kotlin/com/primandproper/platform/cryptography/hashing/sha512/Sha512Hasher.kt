package com.primandproper.platform.cryptography.hashing.sha512

import com.primandproper.platform.cryptography.hashing.Hasher
import java.security.MessageDigest

/** A [Hasher] backed by SHA-512. Port of platform-go's `sha512.NewSHA512Hasher`. */
public object Sha512Hasher : Hasher {
    override fun hash(content: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(content)
}
