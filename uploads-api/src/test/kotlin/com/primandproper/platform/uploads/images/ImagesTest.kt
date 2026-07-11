package com.primandproper.platform.uploads.images

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.isError
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `uploads/images` validation tests (`images_test.go`). */
class ImagesTest {
    private fun encode(
        format: String,
        width: Int = 4,
        height: Int = 4,
    ): ByteArray {
        val type = if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val img = BufferedImage(width, height, type)
        val bos = ByteArrayOutputStream()
        assertTrue(ImageIO.write(img, format, bos), "no writer for $format")
        return bos.toByteArray()
    }

    @Test
    fun `decodes png, jpeg, and gif and detects the content type from the data`() {
        assertEquals("image/png", decodeImage(encode("png")).contentType)
        assertEquals("image/jpeg", decodeImage(encode("jpeg")).contentType)
        assertEquals("image/gif", decodeImage(encode("gif")).contentType)
    }

    @Test
    fun `rejects a supported-but-unwanted format`() {
        // BMP has an ImageIO decoder, but is not one of PNG/JPEG/GIF, so it must be rejected by type.
        val err = assertFailsWith<PlatformException> { decodeImage(encode("bmp")) }
        assertTrue(isError<InvalidImageContentTypeException>(err))
    }

    @Test
    fun `rejects undecodable data`() {
        val err = assertFailsWith<PlatformException> { decodeImage(byteArrayOf(1, 2, 3, 4, 5)) }
        assertTrue(isError<InvalidImageContentTypeException>(err))
    }

    @Test
    fun `rejects an image whose dimensions exceed the maximum`() {
        val err = assertFailsWith<PlatformException> { decodeImage(encode("png", width = MAX_IMAGE_DIMENSION + 1, height = 1)) }
        assertTrue(isError<ImageTooLargeException>(err))
    }

    @Test
    fun `dataUri renders a base64 data uri`() {
        val uri = decodeImage(encode("png")).dataUri()
        assertTrue(uri.startsWith("data:image/png;base64,"))
    }

    @Test
    fun `thumbnail rejects zero dimensions and is otherwise a documented seam`() {
        val img = decodeImage(encode("png"))
        val dimErr = assertFailsWith<PlatformException> { img.thumbnail(0, 10) }
        assertTrue(isError<InvalidThumbnailDimensionsException>(dimErr))
        assertFailsWith<NotImplementedError> { img.thumbnail(2, 2) }
    }
}
