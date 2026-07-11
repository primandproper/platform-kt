package com.primandproper.platform.qrcodes.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Port of platform-go's `qrcodes/noop/noop_test.go`. */
class NoopQrCodeBuilderTest {
    @Test
    fun `construction yields a non-null builder`() {
        assertNotNull(NoopQrCodeBuilder)
    }

    @Test
    fun `buildQrCode returns an empty string`() =
        runTest {
            val result = NoopQrCodeBuilder.buildQrCode("user@example.com", "JBSWY3DPEHPK3PXP")
            assertEquals("", result)
        }
}
