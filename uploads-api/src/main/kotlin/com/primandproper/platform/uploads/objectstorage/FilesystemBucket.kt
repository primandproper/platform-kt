package com.primandproper.platform.uploads.objectstorage

import com.primandproper.platform.uploads.Attributes
import com.primandproper.platform.uploads.ObjectInfo
import com.primandproper.platform.uploads.SaveOptions
import com.primandproper.platform.uploads.SignedUrlOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.Properties
import java.util.stream.Collectors

/**
 * A filesystem-backed [Bucket], the analog of gocloud.dev's `fileblob`. Objects are stored as files
 * under [FilesystemConfig.rootDirectory]; the stored `Content-Type` / `Cache-Control` ride in a sibling
 * `<path>.attrs` properties sidecar (fileblob keeps an equivalent `.attrs` file), and `modTime`/`size`
 * come from the file's own attributes.
 *
 * Created directories are restricted to the configured [FilesystemConfig.resolvedDirectoryMode] (POSIX
 * `0o700` by default) on platforms that support POSIX permissions — so other users on the host can't
 * traverse in and read stored objects, matching Go's `DirFileMode` guard. Blocking file I/O runs on
 * [Dispatchers.IO]. Signing is unsupported for the unsigned filesystem backend, so [signedUrl] throws.
 */
public class FilesystemBucket(
    config: FilesystemConfig,
) : Bucket {
    private val root: Path = Paths.get(config.rootDirectory).toAbsolutePath().normalize()
    private val dirPermissions: Set<PosixFilePermission>? = posixPermissions(config.resolvedDirectoryMode())

    override suspend fun write(
        path: String,
        source: InputStream,
        options: SaveOptions,
    ): Long =
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            createParentDirectories(target.parent)
            val bytes = source.readBytes()
            Files.write(target, bytes)
            writeSidecar(target, options)
            bytes.size.toLong()
        }

    override suspend fun newRangeReader(
        path: String,
        offset: Long,
        length: Long,
    ): RangeReaderResult =
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            if (!Files.exists(target)) throw objectNotFound(path)
            val bytes = Files.readAllBytes(target)
            val start = offset.coerceIn(0, bytes.size.toLong()).toInt()
            val end = if (length < 0) bytes.size else (start + length).coerceAtMost(bytes.size.toLong()).toInt()
            val slice = bytes.copyOfRange(start, end)
            RangeReaderResult(slice.inputStream(), slice.size.toLong())
        }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            Files.deleteIfExists(target)
            Files.deleteIfExists(sidecarOf(target))
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { Files.exists(resolve(path)) }

    override suspend fun attributes(path: String): Attributes =
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            if (!Files.exists(target)) throw objectNotFound(path)
            val props = readSidecar(target)
            Attributes(
                contentType = props.getProperty(CONTENT_TYPE_KEY),
                cacheControl = props.getProperty(CACHE_CONTROL_KEY),
                etag = null,
                modTime = Instant.ofEpochMilli(Files.getLastModifiedTime(target).toMillis()),
                size = Files.size(target),
            )
        }

    override fun list(prefix: String): Flow<ObjectInfo> =
        flow {
            if (!Files.exists(root)) return@flow
            val entries = walkObjects()
            for (entry in entries) {
                if (entry.path.startsWith(prefix)) emit(entry)
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun signedUrl(
        path: String,
        options: SignedUrlOptions,
    ): String = throw UnsupportedOperationException("signed URLs are not supported by the unsigned filesystem bucket")

    private fun walkObjects(): List<ObjectInfo> {
        val stream = Files.walk(root)
        try {
            return stream
                .filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(ATTRS_SUFFIX) }
                .map { file ->
                    ObjectInfo(
                        path = root.relativize(file).toString().replace('\\', '/'),
                        modTime = Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()),
                        size = Files.size(file),
                        isDir = false,
                    )
                }
                .collect(Collectors.toList())
        } finally {
            stream.close()
        }
    }

    /** Resolves [path] under [root], rejecting any path that would escape the root (`..` traversal). */
    private fun resolve(path: String): Path {
        val resolved = root.resolve(path).normalize()
        require(resolved.startsWith(root)) { "path escapes the bucket root: \"$path\"" }
        return resolved
    }

    private fun createParentDirectories(dir: Path?) {
        if (dir == null || Files.exists(dir)) return
        if (dirPermissions != null) {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(dirPermissions))
        } else {
            Files.createDirectories(dir)
        }
    }

    private fun writeSidecar(
        target: Path,
        options: SaveOptions,
    ) {
        if (options.contentType == null && options.cacheControl == null) return
        val props = Properties()
        options.contentType?.let { props.setProperty(CONTENT_TYPE_KEY, it) }
        options.cacheControl?.let { props.setProperty(CACHE_CONTROL_KEY, it) }
        Files.newOutputStream(sidecarOf(target)).use { props.store(it, "uploads filesystem bucket attrs") }
    }

    private fun readSidecar(target: Path): Properties {
        val props = Properties()
        val sidecar = sidecarOf(target)
        if (Files.exists(sidecar)) Files.newInputStream(sidecar).use { props.load(it) }
        return props
    }

    private fun sidecarOf(target: Path): Path = target.resolveSibling(target.fileName.toString() + ATTRS_SUFFIX)

    private fun objectNotFound(path: String): Throwable = NoSuchElementException("object not found: $path")

    private companion object {
        const val ATTRS_SUFFIX = ".attrs"
        const val CONTENT_TYPE_KEY = "contentType"
        const val CACHE_CONTROL_KEY = "cacheControl"

        /** Builds a POSIX permission set from a `0o...` mode, or `null` when the filesystem is not POSIX. */
        fun posixPermissions(mode: Int): Set<PosixFilePermission>? =
            try {
                val octal = Integer.toOctalString(mode).padStart(3, '0').takeLast(3)
                PosixFilePermissions.fromString(rwxString(octal))
            } catch (_: Exception) {
                null
            }

        private fun rwxString(octal: String): String =
            buildString {
                for (ch in octal) {
                    val bits = ch - '0'
                    append(if (bits and 4 != 0) 'r' else '-')
                    append(if (bits and 2 != 0) 'w' else '-')
                    append(if (bits and 1 != 0) 'x' else '-')
                }
            }
    }
}
