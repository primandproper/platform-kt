package com.primandproper.platform.email

/**
 * A service that can send emails — the port of platform-go's `email.Emailer`.
 *
 * Go's method threads a `context.Context` and returns an `error`; this port suspends instead
 * (cancellation and trace context ride the coroutine context) and signals failure by throwing, the
 * idiomatic Kotlin shape. Backends live in sibling modules (`:email-resend`); the noop and mock
 * doubles ship here.
 */
public interface Emailer {
    /** Sends the email described by [details], throwing on failure. */
    public suspend fun sendEmail(details: OutboundEmailMessage)
}
