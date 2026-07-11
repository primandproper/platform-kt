package com.primandproper.platform.email.mock

import com.primandproper.platform.email.Emailer
import com.primandproper.platform.email.OutboundEmailMessage

/**
 * A configurable [Emailer] test double, mirroring platform-go's moq-generated
 * `emailmock.EmailerMock`. [sendEmail] delegates to the settable [sendEmailFunc]; calling it while
 * [sendEmailFunc] is `null` throws [IllegalStateException], the same "unmocked call surfaces
 * immediately" behavior moq's generated panic gives. Every call's argument is recorded in
 * [sendEmailCalls], standing in for moq's generated `SendEmailCalls()` accessor.
 *
 * ```
 * val mock = EmailerMock(sendEmailFunc = { /* assert on it */ })
 * ```
 */
public class EmailerMock(
    public var sendEmailFunc: (suspend (OutboundEmailMessage) -> Unit)? = null,
) : Emailer {
    private val lock = Any()
    private val _sendEmailCalls = mutableListOf<OutboundEmailMessage>()

    /** Every [OutboundEmailMessage] passed to [sendEmail], in order. */
    public val sendEmailCalls: List<OutboundEmailMessage>
        get() = synchronized(lock) { _sendEmailCalls.toList() }

    override suspend fun sendEmail(details: OutboundEmailMessage) {
        synchronized(lock) { _sendEmailCalls += details }
        val func = sendEmailFunc ?: error("mock.sendEmailFunc: method is null but was just called")
        func(details)
    }
}
