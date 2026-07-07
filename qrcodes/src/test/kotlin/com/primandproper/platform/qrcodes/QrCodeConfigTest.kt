package com.primandproper.platform.qrcodes

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrCodeConfigTest {
    @Test
    fun `size defaults to 256`() {
        assertEquals(DEFAULT_QR_SIZE, QrCodeConfig("issuer").size)
        assertEquals(256, QrCodeConfig("issuer").size)
    }

    @Test
    fun `newBuilder yields a working builder`() =
        runTest {
            val builder = QrCodeConfig("configured-issuer").newBuilder()
            val dataUri = builder.buildQrCode("user", "secret")
            assertTrue(dataUri.startsWith("data:image/png;base64,"))
        }
}
