package com.primandproper.platform.uploads

import com.primandproper.platform.uploads.objectstorage.StorageConfig

/**
 * Top-level settings for the uploads object storage. Port of platform-go's `uploads/config.Config`,
 * which wraps the [storage] provider config and a [debug] flag.
 *
 * @param storage the object-storage provider configuration.
 * @param debug whether verbose upload logging is enabled.
 */
public data class UploadsConfig(
    val storage: StorageConfig,
    val debug: Boolean = false,
) {
    /** Validates the nested [storage] config. Port of Go's `Config.ValidateWithContext`. */
    public fun validate(): Unit = storage.validate()
}
