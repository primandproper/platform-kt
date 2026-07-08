package com.primandproper.platform.qrcodes

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Port of platform-go's `qrcodes/qrcodes_test.go`. */
class DefaultQrCodeBuilderTest {
    private companion object {
        const val ISSUER = "test-issuer"
        const val DATA_URI_PREFIX = "data:image/png;base64,"

        // PNG magic header: 0x89 'P' 'N' 'G'.
        val PNG_MAGIC = listOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())

        fun decodeDataUri(dataUri: String): ByteArray {
            assertTrue(
                dataUri.startsWith(DATA_URI_PREFIX),
                "expected a PNG data URI, got: ${dataUri.take(32)}",
            )
            return Base64.getDecoder().decode(dataUri.removePrefix(DATA_URI_PREFIX))
        }
    }

    @Test
    fun `standard construction yields a non-null builder`() {
        val builder = DefaultQrCodeBuilder(ISSUER)
        assertTrue(builder is QrCodeBuilder)
    }

    @Test
    fun `buildQrCode returns a non-empty PNG data URI`() =
        runTest {
            val builder = DefaultQrCodeBuilder(ISSUER)
            val actual = builder.buildQrCode("username", "two-factor-secret")

            // The image is PNG-encoded, so the data URI must advertise image/png.
            assertTrue(actual.startsWith(DATA_URI_PREFIX))
            val png = decodeDataUri(actual)
            assertTrue(png.isNotEmpty())
            assertEquals(PNG_MAGIC, png.take(4))
        }

    @Test
    fun `size parameter is honored`() =
        runTest {
            val small = decodeDataUri(DefaultQrCodeBuilder(ISSUER, size = 256).buildQrCode("u", "s"))
            val large = decodeDataUri(DefaultQrCodeBuilder(ISSUER, size = 512).buildQrCode("u", "s"))

            assertEquals(256, ImageIO.read(ByteArrayInputStream(small)).width)
            assertEquals(512, ImageIO.read(ByteArrayInputStream(large)).width)
        }

    @Test
    fun `empty username and secret still yields a valid PNG`() =
        runTest {
            // The otpauth URI is non-empty even for empty inputs, so encoding still succeeds.
            val png = decodeDataUri(DefaultQrCodeBuilder(ISSUER).buildQrCode("", ""))
            assertEquals(PNG_MAGIC, png.take(4))
        }

    @Test
    fun `content exceeding QR capacity throws`() =
        runTest {
            val builder = DefaultQrCodeBuilder(ISSUER)
            // A username far longer than the maximum QR-code capacity forces the encoder to fail.
            assertFailsWith<Exception> {
                builder.buildQrCode("a".repeat(4000), "two-factor-secret")
            }
        }

    @Test
    fun `buildQrCode observes an operation recording username and length`() =
        runTest {
            val obs = RecordingObserver()
            val builder = DefaultQrCodeBuilder(ISSUER, 256, obs)

            builder.buildQrCode("username", "two-factor-secret")

            // The BuildQRCode operation must open, record the username and length, end, and see no errors.
            val op = obs.operations.last { it.name == "BuildQRCode" }
            assertTrue(op.ended)
            assertTrue(op.errors.isEmpty())
            assertEquals("username", op.values[Keys.USERNAME])
            assertTrue((op.values[Keys.LENGTH] as Int) > 0)
        }

    @Test
    fun `buildOtpAuthUri escapes issuer, username, and secret with reserved characters`() {
        val uri = buildOtpAuthUri("My & App", "user name", "SECRET&123")

        val parsed = URI(uri)
        assertEquals("otpauth", parsed.scheme)
        assertEquals("totp", parsed.host)
        // URI decodes %XX escapes on access, so the label round-trips to the literal "issuer:username".
        assertContains(parsed.path, "My & App:user name")

        val query = decodeQuery(uri)
        assertEquals("My & App", query["issuer"])
        assertEquals("SECRET&123", query["secret"])
    }

    private fun decodeQuery(uri: String): Map<String, String> =
        uri.substringAfter('?').split("&").associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            key to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
}
