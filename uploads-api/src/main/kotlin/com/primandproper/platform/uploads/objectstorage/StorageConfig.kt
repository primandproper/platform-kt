package com.primandproper.platform.uploads.objectstorage

import com.primandproper.platform.errors.PlatformException

/** Thrown when the configured provider is not recognized. Port of Go's `objectstorage.ErrUnknownProvider`. */
public class UnknownProviderException : PlatformException("unknown storage provider")

/** Thrown when a [StorageConfig] fails validation. Carries the same messages Go's ozzo-validation produces. */
public class StorageConfigException(
    message: String,
) : PlatformException(message)

/**
 * The mode (as a POSIX permission bits integer) for directories the filesystem backend creates.
 * `0o700` (owner-only) is the default — used instead of a world-readable default so other users on the
 * host can't traverse into the upload directory and read stored objects. Port of Go's
 * `defaultDirectoryMode`.
 */
public const val DEFAULT_DIRECTORY_MODE: Int = 0x1C0 // 0o700

/**
 * Configures a filesystem-based provider. Port of Go's `objectstorage.FilesystemConfig`.
 *
 * @param rootDirectory the directory objects are stored under; required.
 * @param directoryMode the POSIX permission bits for directories the backend creates; `null` (the
 *   default) or a non-positive value resolves to [DEFAULT_DIRECTORY_MODE] — `null` being the Kotlin
 *   analog of Go's zero-value sentinel.
 */
public data class FilesystemConfig(
    val rootDirectory: String,
    val directoryMode: Int? = null,
) {
    /** Returns the configured directory mode, or the `0o700` default. Port of Go's `directoryMode()`. */
    public fun resolvedDirectoryMode(): Int = directoryMode?.takeIf { it > 0 } ?: DEFAULT_DIRECTORY_MODE

    /** Validates the config, throwing when [rootDirectory] is blank. */
    public fun validate() {
        if (rootDirectory.isBlank()) throw StorageConfigException("rootDirectory: cannot be blank")
    }
}

/**
 * Configures a Cloudflare R2 provider. Port of Go's `objectstorage.R2Config`. Consumed by `:uploads-s3`
 * (R2 is S3-compatible); a documented `TODO(r2)` seam until that backend lands.
 */
public data class R2Config(
    val accountId: String,
    val accessKeyId: String,
    val secretAccessKey: String,
) {
    /** Validates the config, throwing when any field is blank. */
    public fun validate() {
        if (accountId.isBlank()) throw StorageConfigException("accountID: cannot be blank")
        if (accessKeyId.isBlank()) throw StorageConfigException("accessKeyID: cannot be blank")
        if (secretAccessKey.isBlank()) throw StorageConfigException("secretAccessKey: cannot be blank")
    }
}

/**
 * Configures a Backblaze B2 provider. Port of Go's `objectstorage.BackblazeB2Config`. Consumed by
 * `:uploads-s3` (B2 is S3-compatible); a documented `TODO(b2)` seam until that backend lands.
 */
public data class BackblazeB2Config(
    val applicationKeyId: String,
    val applicationKey: String,
    val region: String,
) {
    /** Validates the config, throwing when any field is blank. */
    public fun validate() {
        if (applicationKeyId.isBlank()) throw StorageConfigException("applicationKeyID: cannot be blank")
        if (applicationKey.isBlank()) throw StorageConfigException("applicationKey: cannot be blank")
        if (region.isBlank()) throw StorageConfigException("region: cannot be blank")
    }
}

/**
 * Configures an object-storage [Uploader]. Port of platform-go's `objectstorage.Config`.
 *
 * [provider] is a typed [StorageProvider], resolved from its string form once at the parse edge
 * ([StorageProvider.fromValue]); validation, the conditional sub-config rules, and backend dispatch all
 * consume the typed value, so the "unreachable null arm" a string field forced is gone. The conditional
 * rules match Go exactly: the filesystem/R2/B2 sub-config is required when its provider is selected and
 * must be `null` otherwise.
 *
 * The circuit-breaker settings on Go's `Config` are omitted — `:circuitbreaking` is outside this port's
 * dependency set (see the `TODO(circuitbreaking)` seam on [Uploader]).
 *
 * @param bucketName the bucket/container name; required.
 * @param provider the selected [StorageProvider].
 * @param bucketPrefix an optional key prefix applied to every path (Go's `blob.PrefixedBucket`).
 */
public data class StorageConfig(
    val bucketName: String,
    val provider: StorageProvider,
    val bucketPrefix: String = "",
    val filesystemConfig: FilesystemConfig? = null,
    val r2Config: R2Config? = null,
    val backblazeB2Config: BackblazeB2Config? = null,
) {
    /**
     * Validates the config, mirroring Go's `Config.ValidateWithContext`:
     * - [bucketName] is required;
     * - the matching sub-config is required for filesystem/R2/B2, and must be `null` for any other
     *   provider (Go's `validation.When(...).Else(validation.Nil)`).
     */
    public fun validate() {
        if (bucketName.isBlank()) throw StorageConfigException("bucketName: cannot be blank")

        requireSubConfig(provider == StorageProvider.FILESYSTEM, filesystemConfig, "filesystemConfig")
        requireSubConfig(provider == StorageProvider.R2, r2Config, "r2Config")
        requireSubConfig(provider == StorageProvider.BACKBLAZE_B2, backblazeB2Config, "backblazeB2Config")

        filesystemConfig?.validate()
        r2Config?.validate()
        backblazeB2Config?.validate()
    }

    private fun requireSubConfig(
        expected: Boolean,
        value: Any?,
        name: String,
    ) {
        if (expected && value == null) throw StorageConfigException("$name: is required")
        if (!expected && value != null) throw StorageConfigException("$name: must be blank")
    }
}
