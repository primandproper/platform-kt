package com.primandproper.platform.cryptography.encryption

/** Base type for the encryption failures. Port of platform-go's `encryption` error vars. */
public open class EncryptionException(message: String) : Exception(message)

/** Thrown when the supplied key is not the required length. Mirrors `ErrIncorrectKeyLength`. */
public class IncorrectKeyLengthException : EncryptionException("secret is not the right length")

/** Thrown when ciphertext is too short to contain a nonce. Mirrors `ErrMalformedCiphertext`. */
public class MalformedCiphertextException : EncryptionException("malformed ciphertext")

/** Thrown when ciphertext fails its authentication check. Mirrors `ErrAuthenticationFailed`. */
public class AuthenticationFailedException : EncryptionException("ciphertext authentication failed")
