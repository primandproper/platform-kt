package com.primandproper.platform.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Mirrors platform-go's `notifications/mobile/config` provider-validation cases. */
class NotificationConfigTest {
    @Test
    fun `every known provider resolves from its value`() {
        assertEquals(NotificationProvider.APNS_FCM, NotificationProvider.fromValue("apns_fcm"))
        assertEquals(NotificationProvider.NOOP, NotificationProvider.fromValue("noop"))
    }

    @Test
    fun `fromValue trims and lowercases`() {
        assertEquals(NotificationProvider.APNS_FCM, NotificationProvider.fromValue("  APNS_FCM "))
    }

    @Test
    fun `fromValue returns null for an unknown provider`() {
        assertNull(NotificationProvider.fromValue("onesignal"))
    }

    @Test
    fun `validate accepts a known provider`() {
        NotificationConfig(provider = "apns_fcm").validate()
    }

    @Test
    fun `validate rejects an unknown provider`() {
        assertFailsWith<InvalidNotificationProviderException> { NotificationConfig(provider = "onesignal").validate() }
    }

    @Test
    fun `empty provider is permitted for noop fallback`() {
        NotificationConfig(provider = "").validate()
    }
}
