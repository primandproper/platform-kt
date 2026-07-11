package com.primandproper.platform.uploads.images

import com.primandproper.platform.errors.PlatformException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import javax.imageio.ImageIO

/*
 * Small, pure helpers for validating and describing images — bytes in, a validated [ImageContent] out,
 * with no knowledge of HTTP or object storage. Port of platform-go's `uploads/images` package,
 * trimmed to the validation surface (see the `TODO(images)` thumbnail seam below).
 *
 * Supported formats are PNG, JPEG, and GIF; the content type is detected from the data itself (an
 * ImageIO reader's format), never from a filename.
 */

/**
 * Thrown when the image is of an unsupported type. Port of Go's `ErrInvalidImageContentType`.
 * [detail] appends context after the base message (the shape Go's wrapped errors produced).
 */
public class InvalidImageContentTypeException(
    detail: String? = null,
) : PlatformException("invalid image content type" + if (detail != null) ": $detail" else "")

/** Thrown when a zero width or height was requested for a thumbnail. Port of Go's `ErrInvalidThumbnailDimensions`. */
public class InvalidThumbnailDimensionsException :
    PlatformException("thumbnail width and height must both be greater than zero")

/**
 * Thrown when the image exceeds the configured size or dimension limits. Port of Go's `ErrImageTooLarge`.
 * [detail] appends context after the base message (the shape Go's wrapped errors produced).
 */
public class ImageTooLargeException(
    detail: String? = null,
) : PlatformException("image too large" + if (detail != null) ": $detail" else "")

/** Caps the encoded image size read into memory: 32 MiB. Port of Go's `maxImageBytes`. */
public const val MAX_IMAGE_BYTES: Int = 32 shl 20

/**
 * Caps the width and height (in pixels) decoded, guarding against a small file that declares enormous
 * dimensions to force a huge pixel allocation. Port of Go's `maxImageDimension`.
 */
public const val MAX_IMAGE_DIMENSION: Int = 10_000

private const val IMAGE_PNG = "image/png"
private const val IMAGE_JPEG = "image/jpeg"
private const val IMAGE_GIF = "image/gif"

/**
 * A validated, in-memory image with its detected content type. Port of Go's `images.Image`.
 */
public class ImageContent(
    public val contentType: String,
    public val data: ByteArray,
) {
    /** Returns the image encoded as a base64 `data:` URI. Port of Go's `Image.DataURI`. */
    public fun dataUri(): String = "data:$contentType;base64,${Base64.getEncoder().encodeToString(data)}"

    /**
     * Returns a resized copy of the image, re-encoded in its original format.
     *
     * TODO(images): the resize + re-encode transforms (aspect-preserving scaling, EXIF-orientation
     * handling for JPEG, per-frame re-quantizing for animated GIF) are a substantial, allocation-heavy
     * port of Go's `thumbnails.go`/`orientation.go`/`animated_gif.go`. They are intentionally left as a
     * seam so this module stays a pure validation surface. [width]/[height] are validated here so the
     * contract (reject zero dimensions) is already faithful.
     */
    public fun thumbnail(
        width: Int,
        height: Int,
    ): ImageContent {
        if (width <= 0 || height <= 0) throw InvalidThumbnailDimensionsException()
        throw NotImplementedError("TODO(images): thumbnail transforms are not yet ported")
    }
}

/**
 * Reads an image from [source], validating that it is a supported, decodable image and detecting its
 * content type from the data itself. Port of Go's `images.Decode`.
 *
 * The size cap is enforced before validation (reading at most [MAX_IMAGE_BYTES] + 1 bytes), and the
 * declared dimensions are checked from the header before any pixel decode — so an oversized image is
 * rejected before it can force a large allocation, exactly as the Go port checks `DecodeConfig` first.
 *
 * @throws ImageTooLargeException when the bytes or declared dimensions exceed the limits;
 *   [InvalidImageContentTypeException] when the data is not a supported/decodable PNG, JPEG, or GIF.
 */
public fun decodeImage(source: InputStream): ImageContent {
    val data = readLimited(source, MAX_IMAGE_BYTES + 1)
    if (data.size > MAX_IMAGE_BYTES) {
        throw ImageTooLargeException("image exceeds maximum size of $MAX_IMAGE_BYTES bytes")
    }

    ImageIO.createImageInputStream(ByteArrayInputStream(data)).use { iis ->
        if (iis == null) throw InvalidImageContentTypeException("unreadable image data")
        val readers = ImageIO.getImageReaders(iis)
        if (!readers.hasNext()) {
            throw InvalidImageContentTypeException("no decoder for image data")
        }
        val reader = readers.next()
        try {
            reader.input = iis
            // Read the declared dimensions from the header before decoding pixels.
            val width = reader.getWidth(reader.minIndex)
            val height = reader.getHeight(reader.minIndex)
            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                throw ImageTooLargeException(
                    "image dimensions ${width}x$height exceed maximum of $MAX_IMAGE_DIMENSION",
                )
            }

            val contentType = "image/" + reader.formatName.lowercase()
            when (contentType) {
                IMAGE_PNG, IMAGE_JPEG, IMAGE_GIF -> {}
                else -> throw InvalidImageContentTypeException(contentType)
            }

            return ImageContent(contentType = contentType, data = data)
        } finally {
            reader.dispose()
        }
    }
}

/** Reads an image from raw [bytes]. Convenience over [decodeImage]. */
public fun decodeImage(bytes: ByteArray): ImageContent = decodeImage(ByteArrayInputStream(bytes))

/** Reads at most [limit] bytes from [source] (the Go port reads `LimitReader(r, maxImageBytes+1)`). */
private fun readLimited(
    source: InputStream,
    limit: Int,
): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = source.read(buffer, total, limit - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}
