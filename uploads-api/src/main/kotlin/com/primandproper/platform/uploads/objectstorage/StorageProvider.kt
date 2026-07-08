package com.primandproper.platform.uploads.objectstorage

/**
 * The supported storage providers. Port of platform-go's `objectstorage` provider constants
 * (`FilesystemProvider`, `MemoryProvider`, `S3Provider`, …). [value] is the wire/string form validated
 * against configuration.
 *
 * [MEMORY] and [FILESYSTEM] are served by the buckets in this module; [S3] is served by `:uploads-s3`;
 * [GCP], [R2], and [BACKBLAZE_B2] are documented seams (`TODO(gcs)` / `TODO(r2)` / `TODO(b2)`) — the
 * enum lists them so config validation accepts them, but no backend is wired here yet.
 */
public enum class StorageProvider(
    public val value: String,
) {
    FILESYSTEM("filesystem"),
    MEMORY("memory"),
    S3("s3"),
    GCP("gcp"),
    R2("r2"),
    BACKBLAZE_B2("backblaze_b2"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(...)` rejecting an unknown provider,
         * after the `Config.ValidateWithContext` canonicalization (trim + lowercase).
         */
        public fun fromValue(value: String): StorageProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}
