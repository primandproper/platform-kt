package com.primandproper.platform.email.mock

import com.primandproper.platform.email.OutboundEmailMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Mirrors the contract of platform-go's moq-generated `emailmock.EmailerMock`. */
class EmailerMockTest {
    private fun message(subject: String) =
        OutboundEmailMessage(
            toAddress = "to@example.com",
            fromAddress = "from@example.com",
            subject = subject,
            htmlContent = "<p>body</p>",
        )

    @Test
    fun `delegates to sendEmailFunc and records the call`() =
        runTest {
            val seen = mutableListOf<OutboundEmailMessage>()
            val mock = EmailerMock(sendEmailFunc = { seen += it })

            val msg = message("welcome")
            mock.sendEmail(msg)

            assertEquals(listOf(msg), seen)
            assertEquals(listOf(msg), mock.sendEmailCalls)
        }

    @Test
    fun `calling with a null func throws`() =
        runTest {
            assertFailsWith<IllegalStateException> { EmailerMock().sendEmail(message("x")) }
        }
}
