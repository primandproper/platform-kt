package com.primandproper.platform.authentication.totp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProvisioningUriTest {
    @Test
    fun `builds an otpauth URI with defaults`() {
        val uri = totpProvisioningUri("Example", "alice@example.com", "JBSWY3DPEHPK3PXP")
        assertEquals(
            "otpauth://totp/Example:alice%40example.com" +
                "?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30",
            uri,
        )
    }

    @Test
    fun `percent-encodes spaces in the issuer and account`() {
        val uri = totpProvisioningUri("Big Corp", "user name", "ABCDEF", TotpOptions(digits = TotpDigits.EIGHT))
        assertTrue(uri.startsWith("otpauth://totp/Big%20Corp:user%20name?"))
        assertTrue(uri.contains("issuer=Big%20Corp"))
        assertTrue(uri.contains("digits=8"))
    }
}
