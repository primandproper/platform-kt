package com.primandproper.platform.email

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Mirrors platform-go's `email/config/config_test.go` provider-validation cases. */
class EmailConfigTest {
    @Test
    fun `every known provider resolves from its value`() {
        assertEquals(EmailProvider.SENDGRID, EmailProvider.fromValue("sendgrid"))
        assertEquals(EmailProvider.MAILGUN, EmailProvider.fromValue("mailgun"))
        assertEquals(EmailProvider.MAILJET, EmailProvider.fromValue("mailjet"))
        assertEquals(EmailProvider.RESEND, EmailProvider.fromValue("resend"))
        assertEquals(EmailProvider.POSTMARK, EmailProvider.fromValue("postmark"))
        assertEquals(EmailProvider.SES, EmailProvider.fromValue("ses"))
    }

    @Test
    fun `fromValue trims and lowercases`() {
        assertEquals(EmailProvider.RESEND, EmailProvider.fromValue("  ReSeNd "))
    }

    @Test
    fun `fromValue returns null for an unknown provider`() {
        assertNull(EmailProvider.fromValue("sendgird"))
    }

    @Test
    fun `fromConfigValue resolves a known provider`() {
        assertEquals(EmailProvider.RESEND, EmailProvider.fromConfigValue("resend"))
    }

    @Test
    fun `fromConfigValue rejects an unknown provider`() {
        assertFailsWith<InvalidEmailProviderException> { EmailProvider.fromConfigValue("sendgird") }
    }

    @Test
    fun `fromConfigValue maps a blank provider to null for the noop fallback`() {
        assertNull(EmailProvider.fromConfigValue(""))
    }

    @Test
    fun `a null provider config selects the noop emailer`() {
        assertNull(EmailConfig().provider)
    }
}
