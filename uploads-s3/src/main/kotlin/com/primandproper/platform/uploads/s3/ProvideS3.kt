package com.primandproper.platform.uploads.s3

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.uploads.objectstorage.StorageConfig
import com.primandproper.platform.uploads.objectstorage.StorageProvider
import com.primandproper.platform.uploads.objectstorage.Uploader
import com.primandproper.platform.uploads.objectstorage.maybePrefixed
import software.amazon.awssdk.services.s3.S3AsyncClient

/**
 * Builds an instrumented [Uploader] backed by S3. Port of the S3 branch of platform-go's
 * `objectstorage.NewUploadManager`/`selectBucket`: it opens the S3 [S3Bucket], applies any
 * [StorageConfig.bucketPrefix], and wraps the result in the shared [Uploader] so spans/instrumentation
 * behave identically to the in-tree backends.
 *
 * @param s3Client an override [S3Client] (a fake in tests); when `null` a real [AwsS3Client] is built
 *   from a lazily-connecting [S3AsyncClient].
 *
 * TODO(r2) / TODO(b2): Cloudflare R2 and Backblaze B2 are S3-compatible — platform-go opens them as an
 * `s3blob` bucket with a custom endpoint + static credentials. Wiring the endpoint override from
 * [StorageConfig.r2Config] / [StorageConfig.backblazeB2Config] is the remaining work; until then these
 * providers are documented seams. TODO(gcs): GCS uses a non-S3 client entirely and is a separate port.
 */
public fun newS3UploadManager(
    config: StorageConfig,
    s3Client: S3Client? = null,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
): Uploader {
    config.validate()

    val client =
        s3Client ?: when (config.resolvedProvider()) {
            StorageProvider.S3 -> AwsS3Client(S3AsyncClient.builder().build(), config.bucketName)
            StorageProvider.R2 ->
                throw UnsupportedOperationException("TODO(r2): wire the R2 endpoint override from r2Config")
            StorageProvider.BACKBLAZE_B2 ->
                throw UnsupportedOperationException("TODO(b2): wire the Backblaze B2 endpoint override from backblazeB2Config")
            else ->
                throw UnsupportedOperationException("newS3UploadManager only serves the s3 provider; got \"${config.provider}\"")
        }

    return Uploader(maybePrefixed(S3Bucket(client), config.bucketPrefix), config.bucketName, logger, tracerProvider)
}
