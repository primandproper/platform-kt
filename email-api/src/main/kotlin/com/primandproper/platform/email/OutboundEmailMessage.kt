package com.primandproper.platform.email

/**
 * The collection of fields useful for sending a single outbound email. Port of platform-go's
 * `email.OutboundEmailMessage`.
 *
 * [toName] / [fromName] are optional display names paired with the addresses; a backend combines each
 * name with its address into a single header value (see `:email-resend`'s `formatAddress`). [userID]
 * and [testID] are correlation fields that ride along for observability/testing and are not sent on
 * the wire.
 */
public data class OutboundEmailMessage(
    val toAddress: String,
    val fromAddress: String,
    val subject: String,
    val htmlContent: String,
    val toName: String = "",
    val fromName: String = "",
    val userID: String = "",
    val testID: String = "",
)
