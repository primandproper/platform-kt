package com.primandproper.platform.uploads.objectstorage

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import com.primandproper.platform.uploads.Attributer
import com.primandproper.platform.uploads.Attributes
import com.primandproper.platform.uploads.Lister
import com.primandproper.platform.uploads.ObjectInfo
import com.primandproper.platform.uploads.RangeReader
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.SignedUrlOptions
import com.primandproper.platform.uploads.UploadManager
import com.primandproper.platform.uploads.UrlSigner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.InputStream

/**
 * The instrumented [UploadManager] over a provider [Bucket]. Port of platform-go's
 * `objectstorage.Uploader` (`files.go`): every operation opens an [Observer] span, records the filename
 * (or listing prefix/count) on both pillars, and — for reads — the object length, mirroring
 * `u.o11y.Begin(ctx)` / `op.Set(keys.FilenameKey, path)`. The `span` scope records and rethrows any
 * thrown error exactly once, standing in for Go's `op.Error(err, ...)`.
 *
 * It implements the full capability set (range reads, signing, attributes, listing), delegating each to
 * the wrapped [Bucket] — so `manager as? UrlSigner` succeeds against the backend's real support (an
 * unsupported [Bucket.signedUrl] still surfaces its [UnsupportedOperationException]).
 *
 * TODO(circuitbreaking): platform-go wraps every operation in a `circuitbreaking.CircuitBreaker`
 * (short-circuiting while open, counting successes/failures). `:circuitbreaking` is outside this port's
 * dependency set, so the breaker is a documented seam — inject one here once it is available downstream.
 *
 * TODO(metrics): Go records save/read/delete/error counters and a latency histogram through a metrics
 * provider; there is no metrics pillar in platform-kt's observability-api yet.
 */
public class Uploader internal constructor(
    private val o11y: Observer,
    private val bucket: Bucket,
) : UploadManager,
    RangeReader,
    UrlSigner,
    Attributer,
    Lister {
    /**
     * @param bucket the provider surface to instrument.
     * @param bucketName names the component for spans/logs (`"<bucket>_uploader"`), mirroring Go's
     *   `serviceName`.
     * @param logger optional root logger; defaults to noop.
     * @param tracerProvider optional tracer provider; defaults to noop tracing.
     */
    public constructor(
        bucket: Bucket,
        bucketName: String,
        logger: Logger = NoopLogger,
        tracerProvider: TracerProvider = NoopTracerProvider,
    ) : this(Observer("${bucketName}_uploader", logger, tracerProvider), bucket)

    override suspend fun save(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ) {
        o11y.span("Save") {
            set(Keys.FILENAME, path)
            val written = bucket.write(path, source, options)
            set(Keys.LENGTH, written)
        }
    }

    override suspend fun open(path: String): InputStream = openRange(path, 0, -1)

    override suspend fun openRange(
        path: String,
        offset: Long,
        length: Long,
    ): InputStream =
        o11y.span("Open") {
            set(Keys.FILENAME, path)
            val reader = bucket.newRangeReader(path, offset, length)
            set(Keys.LENGTH, reader.size)
            reader.stream
        }

    override suspend fun delete(path: String) {
        o11y.span("Delete") {
            set(Keys.FILENAME, path)
            bucket.delete(path)
        }
    }

    override suspend fun exists(path: String): Boolean =
        o11y.span("Exists") {
            set(Keys.FILENAME, path)
            bucket.exists(path)
        }

    override suspend fun attributes(path: String): Attributes =
        o11y.span("Attributes") {
            set(Keys.FILENAME, path)
            bucket.attributes(path)
        }

    /**
     * Streams the objects under [prefix]. The span opens when collection starts and ends when it
     * completes (or is cancelled), recording the prefix and the object count — mirroring Go's `List`
     * span bracketing the whole iteration.
     *
     * A [channelFlow] is used rather than a plain `flow { }` because the [span] scope runs its block in
     * a child context (it installs the span via `withContext`), and emitting across that context change
     * would trip Flow's context-preservation invariant; `send` from the producer scope is context-safe.
     */
    override fun list(prefix: String): Flow<ObjectInfo> =
        channelFlow {
            o11y.span("List") {
                set("prefix", prefix)
                var count = 0
                bucket.list(prefix).collect { obj ->
                    count++
                    send(obj)
                }
                set("object.count", count)
            }
        }

    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String =
        o11y.span("SignedURL") {
            set(Keys.FILENAME, path)
            bucket.signedUrl(path, options)
        }
}
