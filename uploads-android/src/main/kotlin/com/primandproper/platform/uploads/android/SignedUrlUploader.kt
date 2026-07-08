package com.primandproper.platform.uploads.android

import com.primandproper.platform.httpclient.HttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import java.io.InputStream

/** The outcome of a signed-URL upload: the response [statusCode] and the storage-assigned [etag], if any. */
public data class UploadResult(
    val statusCode: Int,
    val etag: String? = null,
)

/** Thrown when a signed-URL upload returns a non-2xx status. Carries the [statusCode] and any response body. */
public class SignedUrlUploadException(
    public val statusCode: Int,
    public val responseBody: String,
) : RuntimeException("signed URL upload failed with status $statusCode")

/**
 * Uploads object bytes to a pre-signed PUT URL over the [HttpClient] contract — the Android client-side
 * counterpart to the server's `URLSigner` capability. The server mints a short-lived, signed URL that
 * grants a single PUT; this uploader streams the bytes straight to storage, so the file never proxies
 * through the service.
 *
 * The upload is instrumented with an [Observer] span recording the byte length and response status. The
 * signed URL itself is deliberately **not** recorded — it embeds a signature/credential, so logging it
 * would leak a bearer secret; only the object length and status code are observed.
 *
 * The concrete HTTP backend (`:httpclient-okhttp`) is injected, so this class is tested off-device
 * against `FakeHttpClient` with no network.
 */
public class SignedUrlUploader internal constructor(
    private val httpClient: HttpClient,
    private val o11y: Observer,
) {
    /**
     * @param httpClient the backend used to send the PUT.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        httpClient: HttpClient,
        logger: Logger? = null,
        tracerProvider: TracerProvider? = null,
    ) : this(httpClient, Observer(NAME, logger, tracerProvider))

    /**
     * PUTs [body] to [signedUrl]. When [contentType] is non-null it is sent as the `Content-Type` header
     * — for many signed PUT URLs the signature covers this header, so it must match exactly what the URL
     * was signed for.
     *
     * @throws SignedUrlUploadException when the response status is not 2xx.
     */
    public suspend fun upload(
        signedUrl: String,
        body: ByteArray,
        contentType: String? = null,
    ): UploadResult =
        o11y.span("Upload") {
            set(Keys.LENGTH, body.size)

            val request =
                HttpRequest.build {
                    method = HttpMethod.PUT
                    url = signedUrl
                    contentType?.let { header("Content-Type", it) }
                    body(body)
                }

            val response = httpClient.execute(request)
            set(Keys.RESPONSE_STATUS, response.statusCode)

            if (!response.isSuccessful) {
                throw error(
                    SignedUrlUploadException(response.statusCode, response.bodyAsText()),
                    "signed URL upload failed",
                )
            }

            UploadResult(statusCode = response.statusCode, etag = response.headers.first("ETag"))
        }

    /** Streams [source] to [signedUrl], buffering it into memory first. The caller retains ownership of [source]. */
    public suspend fun upload(
        signedUrl: String,
        source: InputStream,
        contentType: String? = null,
    ): UploadResult = upload(signedUrl, source.readBytes(), contentType)

    private companion object {
        const val NAME = "signed_url_uploader"
    }
}
