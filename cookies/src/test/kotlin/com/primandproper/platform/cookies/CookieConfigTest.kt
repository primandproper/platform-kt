package com.primandproper.platform.cookies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Port of platform-go's `cookies/config_test.go` (`TestConfig_ValidateWithContext`). */
class CookieConfigTest {
    private fun baseConfig() =
        CookieConfig(
            cookieName = "platform_cookie",
            base64EncodedHashKey = "hash-key",
            base64EncodedBlockKey = "block-key",
            lifetime = 24.hours,
        )

    @Test
    fun `standard config validates`() {
        baseConfig().validate()
    }

    @Test
    fun `lifetime below minimum is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            baseConfig().copy(lifetime = 1.minutes).validate()
        }
    }

    @Test
    fun `a session cookie with zero lifetime is allowed`() {
        // Mirrors buildConfigForTest passing validation in NewCookieManager despite a zero Lifetime:
        // Go's validation.Min skips the zero value.
        baseConfig().copy(lifetime = kotlin.time.Duration.ZERO).validate()
    }

    @Test
    fun `missing name is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            baseConfig().copy(cookieName = "").validate()
        }
    }

    @Test
    fun `missing hash key is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            baseConfig().copy(base64EncodedHashKey = "").validate()
        }
    }

    @Test
    fun `missing block key is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            baseConfig().copy(base64EncodedBlockKey = "").validate()
        }
    }

    @Test
    fun `explicit SameSite strict validates`() {
        baseConfig().copy(sameSite = SameSite.STRICT.value).validate()
    }

    @Test
    fun `unsupported SameSite value is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            baseConfig().copy(sameSite = "sideways").validate()
        }
    }

    @Test
    fun `SameSite none without secureOnly is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            baseConfig().copy(sameSite = SameSite.NONE.value, secureOnly = false).validate()
        }
    }

    @Test
    fun `SameSite none with secureOnly validates`() {
        baseConfig().copy(sameSite = SameSite.NONE.value, secureOnly = true).validate()
    }

    @Test
    fun `SameSite resolution defaults to lax and is case-insensitive`() {
        assertEquals(SameSite.LAX, SameSite.fromConfigValue(""))
        assertEquals(SameSite.LAX, SameSite.fromConfigValue("unexpected"))
        assertEquals(SameSite.STRICT, SameSite.fromConfigValue("  Strict "))
        assertEquals(SameSite.NONE, SameSite.fromConfigValue("NONE"))
    }
}
