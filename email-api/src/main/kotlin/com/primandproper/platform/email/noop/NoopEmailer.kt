package com.primandproper.platform.email.noop

import com.primandproper.platform.email.Emailer
import com.primandproper.platform.email.OutboundEmailMessage

/**
 * An [Emailer] that discards every message — the port of platform-go's `email/noop.emailer`. The safe
 * default the config factory falls back to when no provider is selected, and for tests that don't
 * care about real delivery.
 */
public class NoopEmailer : Emailer {
    override suspend fun sendEmail(details: OutboundEmailMessage) {
    }
}
