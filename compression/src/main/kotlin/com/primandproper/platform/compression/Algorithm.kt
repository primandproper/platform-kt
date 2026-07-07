package com.primandproper.platform.compression

/**
 * Identifies a supported compression algorithm. Port of platform-go's `Algorithm` named-string
 * type: in Go the value is a plain `string` so a runtime config value can be converted directly
 * (`Algorithm(cfg.Algorithm)`); here it is an enum whose [value] is the wire/config string and
 * [fromValue] performs the same lookup, returning `null` for an unknown name so the caller can
 * raise [InvalidAlgorithmException] — the analog of Go's `ErrInvalidAlgorithm`.
 */
public enum class Algorithm(
    public val value: String,
) {
    /** Selects the Zstandard compression algorithm. Mirrors Go's `AlgorithmZstd`. */
    ZSTD("zstd"),

    /** Selects the S2 compression algorithm. Mirrors Go's `AlgorithmS2`. */
    S2("s2"),
    ;

    public companion object {
        /**
         * Resolves an [Algorithm] from its string [value] (trimmed, case-insensitive), or `null`
         * if it names no known algorithm — mirroring Go's `NewCompressor` rejecting an unknown
         * `Algorithm` with `ErrInvalidAlgorithm`.
         */
        public fun fromValue(value: String): Algorithm? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}
