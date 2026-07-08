package com.primandproper.platform.uploads.s3

import kotlinx.coroutines.future.await
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * The production [S3Client], adapting the AWS SDK v2 `S3AsyncClient`. Each SDK call returns a
 * `CompletableFuture`, bridged to a coroutine with `kotlinx.coroutines.future.await()` — the recommended
 * way to drive the async client from `suspend` code, mirroring how `:cache-redis`'s `LettuceRedisClient`
 * bridges `RedisFuture`.
 *
 * The [client] is injected so it can be built once (with region/credentials from the default provider
 * chain) and shared; it connects lazily on first request, so constructing this adapter never requires a
 * reachable bucket. [bucket] is the S3 bucket every key targets.
 */
public class AwsS3Client(
    private val client: S3AsyncClient,
    private val bucket: String,
) : S3Client {
    override suspend fun putObject(
        key: String,
        data: ByteArray,
        contentType: String?,
        cacheControl: String?,
    ) {
        val request =
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .apply {
                    contentType?.let { contentType(it) }
                    cacheControl?.let { cacheControl(it) }
                }
                .build()
        client.putObject(request, AsyncRequestBody.fromBytes(data)).await()
    }

    override suspend fun getObject(
        key: String,
        rangeHeader: String?,
    ): S3ObjectBytes {
        val request =
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .apply { rangeHeader?.let { range(it) } }
                .build()
        val response = client.getObject(request, AsyncResponseTransformer.toBytes()).await()
        val bytes = response.asByteArray()
        return S3ObjectBytes(bytes, response.response().contentLength() ?: bytes.size.toLong())
    }

    override suspend fun deleteObject(key: String) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()).await()
    }

    override suspend fun headObject(key: String): S3ObjectMetadata? =
        try {
            val response = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).await()
            S3ObjectMetadata(
                contentType = response.contentType(),
                cacheControl = response.cacheControl(),
                etag = response.eTag(),
                lastModified = response.lastModified(),
                contentLength = response.contentLength() ?: 0L,
            )
        } catch (_: NoSuchKeyException) {
            null
        } catch (e: S3Exception) {
            // A HEAD on a missing key can surface as a bare 404 rather than a typed NoSuchKeyException.
            if (e.statusCode() == 404) null else throw e
        }

    override suspend fun listObjects(
        prefix: String,
        continuationToken: String?,
    ): S3ListPage {
        val request =
            ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .apply { continuationToken?.let { continuationToken(it) } }
                .build()
        val response = client.listObjectsV2(request).await()
        val entries =
            response.contents().map { obj ->
                S3ListEntry(key = obj.key(), size = obj.size() ?: 0L, lastModified = obj.lastModified())
            }
        val next = if (response.isTruncated == true) response.nextContinuationToken() else null
        return S3ListPage(entries, next)
    }
}
