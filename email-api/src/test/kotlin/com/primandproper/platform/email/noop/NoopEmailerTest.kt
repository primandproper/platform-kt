package com.primandproper.platform.email.noop

import com.primandproper.platform.email.OutboundEmailMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Mirrors platform-go's `email/noop/noop_test.go`. */
class NoopEmailerTest {
    @Test
    fun `sendEmail is a no-op that does not throw`() =
        runTest {
            NoopEmailer().sendEmail(
                OutboundEmailMessage(
                    toAddress = "to@example.com",
                    fromAddress = "from@example.com",
                    subject = "hi",
                    htmlContent = "<p>hi</p>",
                ),
            )
        }
}
