package com.primandproper.platform.uploads.objectstorage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Port of platform-go's `objectstorage.TestConfig_ValidateWithContext` (and the `config` package test). */
class StorageConfigTest {
    @Test
    fun `standard filesystem config validates`() {
        StorageConfig(
            bucketName = "bucket",
            provider = StorageProvider.FILESYSTEM,
            filesystemConfig = FilesystemConfig(rootDirectory = "/blah"),
        ).validate()
    }

    @Test
    fun `missing bucket name is invalid`() {
        assertFailsWith<StorageConfigException> {
            StorageConfig(bucketName = "", provider = StorageProvider.MEMORY).validate()
        }
    }

    @Test
    fun `provider is resolved from its string form at the parse edge`() {
        // "  S3 " must resolve to s3 (trim + lowercase), matching Go's ValidateWithContext rewrite.
        assertEquals(StorageProvider.S3, StorageProvider.fromValue("  S3 "))
    }

    @Test
    fun `s3, gcp, and memory validate without a sub-config`() {
        for (p in listOf(StorageProvider.S3, StorageProvider.GCP, StorageProvider.MEMORY)) {
            StorageConfig(bucketName = "bucket", provider = p).validate()
        }
    }

    @Test
    fun `r2 requires its config`() {
        assertFailsWith<StorageConfigException> {
            StorageConfig(bucketName = "bucket", provider = StorageProvider.R2).validate()
        }
        StorageConfig(
            bucketName = "bucket",
            provider = StorageProvider.R2,
            r2Config = R2Config("acct", "key", "secret"),
        ).validate()
    }

    @Test
    fun `backblaze requires its config`() {
        assertFailsWith<StorageConfigException> {
            StorageConfig(bucketName = "bucket", provider = StorageProvider.BACKBLAZE_B2).validate()
        }
        StorageConfig(
            bucketName = "bucket",
            provider = StorageProvider.BACKBLAZE_B2,
            backblazeB2Config = BackblazeB2Config("id", "key", "us-west"),
        ).validate()
    }

    @Test
    fun `filesystem requires its config`() {
        assertFailsWith<StorageConfigException> {
            StorageConfig(bucketName = "bucket", provider = StorageProvider.FILESYSTEM).validate()
        }
    }

    @Test
    fun `mismatched sub-config is invalid`() {
        // memory provider carrying a filesystem sub-config must fail (Go's .Else(validation.Nil)).
        assertFailsWith<StorageConfigException> {
            StorageConfig(
                bucketName = "bucket",
                provider = StorageProvider.MEMORY,
                filesystemConfig = FilesystemConfig(rootDirectory = "/blah"),
            ).validate()
        }
    }

    @Test
    fun `unknown provider resolves to null`() {
        assertNull(StorageProvider.fromValue("nope"))
        assertEquals(StorageProvider.BACKBLAZE_B2, StorageProvider.fromValue("BACKBLAZE_B2"))
    }

    @Test
    fun `filesystem directory mode defaults to 0700`() {
        assertEquals(DEFAULT_DIRECTORY_MODE, FilesystemConfig(rootDirectory = "/x").resolvedDirectoryMode())
        assertEquals(DEFAULT_DIRECTORY_MODE, FilesystemConfig(rootDirectory = "/x", directoryMode = 0).resolvedDirectoryMode())
        assertEquals(0x1FF, FilesystemConfig(rootDirectory = "/x", directoryMode = 0x1FF).resolvedDirectoryMode())
    }
}
