package com.primandproper.platform.authentication.argon2

import com.primandproper.platform.authentication.Authenticator
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.Operation
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val NAME = "argon2"

/** Argon2's memory cost, expressed in KiB (64 MiB). Matches platform-go's `sixtyFourMegabytes`. */
private const val MEMORY_KIB = 64 * 1024
private const val ITERATIONS = 1
private const val SALT_LENGTH = 16
private const val KEY_LENGTH = 32

// minParallelism and maxParallelism bound the argon2 parallelism degree, mirroring platform-go. The
// lower bound keeps a floor of concurrency; the upper bound prevents an overflow to 0 when narrowing
// the CPU count on hosts with >255 CPUs, which would make argon2 reject the parallelism degree.
private const val MIN_PARALLELISM = 2
private const val MAX_PARALLELISM = 255

/**
 * The argon2 parallelism degree, clamped to `[MIN_PARALLELISM, MAX_PARALLELISM]` around the host CPU
 * count. Exposed (internal) so the observed test can assert the clamp holds, mirroring platform-go's
 * `TestArgonParams_parallelism`.
 */
internal val parallelism: Int =
    minOf(MAX_PARALLELISM, maxOf(MIN_PARALLELISM, Runtime.getRuntime().availableProcessors()))

/**
 * Builds an Argon2id-backed [Authenticator], the port of platform-go's
 * `argon2.ProvideArgon2Authenticator`.
 *
 * The parameters (64 MiB memory, 1 iteration, a CPU-clamped parallelism degree, a 16-byte salt, and a
 * 32-byte key) match platform-go's `argonParams` exactly, and the encoded-hash wire format matches
 * `alexedwards/argon2id`'s (`$argon2id$v=19$m=…,t=…,p=…$salt$hash`, base64 raw-standard). Because
 * BouncyCastle's [Argon2BytesGenerator] computes the same standard Argon2id x/crypto does, a hash
 * minted by platform-go verifies here and vice-versa.
 *
 * @param observer the observability sink; defaults to a no-op. Tests inject a recording observer.
 */
public fun newArgon2Authenticator(observer: Observer = noopObserver(NAME)): Authenticator = Argon2Authenticator(observer)

private class Argon2Authenticator(
    private val o11y: Observer,
) : Authenticator {
    private val random = SecureRandom()

    override suspend fun hashPassword(password: String): String =
        o11y.span(NAME) {
            recordArgonParams()

            val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
            val hash = deriveKey(password, salt, MEMORY_KIB, ITERATIONS, parallelism, KEY_LENGTH)

            encodeHash(hash, salt, MEMORY_KIB, ITERATIONS, parallelism)
        }

    override suspend fun passwordMatches(
        hash: String,
        password: String,
    ): Boolean =
        o11y.span(NAME) {
            recordArgonParams()

            // A malformed encoded hash throws (matching platform-go's populated `err`); a genuine
            // password mismatch returns false with no error.
            val decoded = decodeHash(hash)
            val computed =
                deriveKey(
                    password,
                    decoded.salt,
                    decoded.memoryKiB,
                    decoded.iterations,
                    decoded.parallelism,
                    decoded.key.size,
                )

            // Time-independent comparison: MessageDigest.isEqual is constant-time on modern JDKs.
            MessageDigest.isEqual(computed, decoded.key)
        }
}

/** Records the argon2 cost parameters on the operation, matching platform-go's `op.SetValues`. */
private fun Operation.recordArgonParams(): Operation =
    set("argon2.memory", MEMORY_KIB)
        .set("argon2.iterations", ITERATIONS)
        .set("argon2.parallelism", parallelism)
        .set("argon2.key_length", KEY_LENGTH)

private fun deriveKey(
    password: String,
    salt: ByteArray,
    memoryKiB: Int,
    iterations: Int,
    parallelism: Int,
    keyLength: Int,
): ByteArray {
    val params =
        Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKiB)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()

    val generator = Argon2BytesGenerator().apply { init(params) }
    val out = ByteArray(keyLength)
    generator.generateBytes(password.toByteArray(Charsets.UTF_8), out)
    return out
}

private fun encodeHash(
    hash: ByteArray,
    salt: ByteArray,
    memoryKiB: Int,
    iterations: Int,
    parallelism: Int,
): String {
    val encoder = Base64.getEncoder().withoutPadding()
    val encodedSalt = encoder.encodeToString(salt)
    val encodedHash = encoder.encodeToString(hash)
    return "\$argon2id\$v=${Argon2Parameters.ARGON2_VERSION_13}\$m=$memoryKiB,t=$iterations,p=$parallelism\$$encodedSalt\$$encodedHash"
}

private class DecodedHash(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: ByteArray,
    val key: ByteArray,
)

/**
 * Parses an `$argon2id$v=19$m=…,t=…,p=…$salt$hash` string into its parameters and raw bytes, throwing
 * [IllegalArgumentException] on anything malformed — the analog of `argon2id.ComparePasswordAndHash`
 * returning a non-nil error for an invalid encoded hash.
 */
private fun decodeHash(encoded: String): DecodedHash {
    val parts = encoded.split("$")
    // A well-formed value splits to ["", "argon2id", "v=19", "m=…,t=…,p=…", salt, hash].
    require(parts.size == 6 && parts[0].isEmpty()) { "malformed argon2 hash" }
    require(parts[1] == "argon2id") { "unsupported argon2 variant: ${parts[1]}" }

    val version = parts[2].removePrefix("v=").toIntOrNull()
    require(version == Argon2Parameters.ARGON2_VERSION_13) { "unsupported argon2 version: ${parts[2]}" }

    val paramFields =
        parts[3].split(",").associate { field ->
            val kv = field.split("=", limit = 2)
            require(kv.size == 2) { "malformed argon2 parameters: ${parts[3]}" }
            kv[0] to kv[1]
        }
    val memory = paramFields["m"]?.toIntOrNull() ?: throw IllegalArgumentException("missing argon2 memory parameter")
    val iterations = paramFields["t"]?.toIntOrNull() ?: throw IllegalArgumentException("missing argon2 iterations parameter")
    val parallelism = paramFields["p"]?.toIntOrNull() ?: throw IllegalArgumentException("missing argon2 parallelism parameter")

    val salt = runCatching { decodeBase64Std(parts[4]) }.getOrElse { throw IllegalArgumentException("malformed argon2 salt", it) }
    val key = runCatching { decodeBase64Std(parts[5]) }.getOrElse { throw IllegalArgumentException("malformed argon2 hash", it) }

    return DecodedHash(memory, iterations, parallelism, salt, key)
}

/**
 * Decodes standard base64, restoring any stripped `=` padding first. The `alexedwards/argon2id` wire
 * format uses raw (unpadded) base64, so pad back to a multiple of 4 before handing it to the JDK
 * decoder rather than relying on the decoder's padding leniency.
 */
private fun decodeBase64Std(value: String): ByteArray {
    val padded =
        when (value.length % 4) {
            2 -> "$value=="
            3 -> "$value="
            else -> value
        }
    return Base64.getDecoder().decode(padded)
}
