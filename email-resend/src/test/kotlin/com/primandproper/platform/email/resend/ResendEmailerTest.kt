package com.primandproper.platform.email.resend

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.ErrCircuitBroken
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.email.EmailKeys
import com.primandproper.platform.email.OutboundEmailMessage
import com.primandproper.platform.httpclient.FakeHttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Mirrors platform-go's `email/resend/resend_test.go`, with `FakeHttpClient` standing in for `httptest`. */
class ResendEmailerTest {
    private val okBody = """{"id":"msg_123"}"""

    /** An emailer wired to a RecordingObserver and FakeHttpClient, so tests assert adaptation offline. */
    private fun recording(
        breaker: CircuitBreaker = NoopCircuitBreaker,
        handler: suspend (HttpRequest) -> HttpResponse = { HttpResponse(statusCode = 200, body = okBody.toByteArray()) },
    ): Triple<ResendEmailer, RecordingObserver, FakeHttpClient> {
        val fake = FakeHttpClient(handler)
        val obs = RecordingObserver()
        val emailer =
            ResendEmailer(
                o11y = obs,
                httpClient = fake,
                circuitBreaker = breaker,
                apiToken = "test-token",
                sendUrl = "https://api.resend.com/emails",
            )
        return Triple(emailer, obs, fake)
    }

    private fun message() =
        OutboundEmailMessage(
            toAddress = "recipient@example.com",
            toName = "Recipient Name",
            fromAddress = "sender@example.com",
            fromName = "Sender Name",
            subject = "the subject line",
            htmlContent = "<p>the html body</p>",
        )

    // --- formatAddress (ports TestFormatAddress) ---

    @Test
    fun `formatAddress returns the bare address when the name is empty`() {
        assertEquals("real@example.com", formatAddress("", "real@example.com"))
        assertEquals("real@example.com", formatAddress("   ", "real@example.com"))
    }

    @Test
    fun `formatAddress quotes a hostile name to prevent recipient injection`() {
        val got = formatAddress("x <a@attacker.com>,", "real@example.com")

        // The hostile chars (< > @ ,) stay inside the quoted name; the real address is the only one
        // outside the quotes, so it cannot be broken out of into a second recipient.
        assertEquals("\"x <a@attacker.com>,\" <real@example.com>", got)
        assertTrue(got.endsWith(" <real@example.com>"))
    }

    @Test
    fun `formatAddress escapes embedded quotes and backslashes`() {
        assertEquals("\"He said \\\"hi\\\"\" <a@b.com>", formatAddress("He said \"hi\"", "a@b.com"))
    }

    // --- construction (ports TestNewResendEmailer) ---

    @Test
    fun `factory with a valid token returns non-null`() {
        assertNotNull(ResendEmailer(apiToken = "test-token", httpClient = FakeHttpClient()))
    }

    @Test
    fun `factory with an empty token throws`() {
        assertFailsWith<EmptyApiTokenException> { ResendEmailer(apiToken = "", httpClient = FakeHttpClient()) }
    }

    @Test
    fun `config factory validates the token`() {
        assertFailsWith<EmptyApiTokenException> {
            ResendEmailer(config = ResendConfig(apiToken = ""), httpClient = FakeHttpClient())
        }
        assertNotNull(ResendEmailer(config = ResendConfig(apiToken = "t"), httpClient = FakeHttpClient()))
    }

    // --- SendEmail (ports TestResendEmailer_SendEmail) ---

    @Test
    fun `sendEmail observes the subject and recipient`() =
        runTest {
            val (emailer, obs, _) = recording()
            val details = message()

            emailer.sendEmail(details)

            obs.assertObservedOperationWithValues(
                EmailKeys.SUBJECT to details.subject,
                EmailKeys.TO_ADDRESS to details.toAddress,
                EmailKeys.FROM_ADDRESS to details.fromAddress,
            )
        }

    @Test
    fun `sendEmail posts the correct request shape`() =
        runTest {
            // Distinct values per field so a from/to or subject/body swap fails this test.
            val (emailer, _, fake) = recording()
            val details = message()

            emailer.sendEmail(details)

            val request = assertNotNull(fake.lastRequest)
            assertEquals(HttpMethod.POST, request.method)
            assertEquals("https://api.resend.com/emails", request.url)
            assertEquals("Bearer test-token", request.headers.first("authorization"))
            assertEquals("application/json", request.headers.first("content-type"))

            val body = Json.parseToJsonElement(String(assertNotNull(request.body))).jsonObject
            assertEquals(formatAddress(details.fromName, details.fromAddress), body["from"]!!.jsonPrimitive.content)
            val to = body["to"]!!.jsonArray
            assertEquals(1, to.size)
            assertEquals(formatAddress(details.toName, details.toAddress), to[0].jsonPrimitive.content)
            assertEquals(details.subject, body["subject"]!!.jsonPrimitive.content)
            assertEquals(details.htmlContent, body["html"]!!.jsonPrimitive.content)
        }

    @Test
    fun `sendEmail records the returned message id on the span`() =
        runTest {
            val (emailer, obs, _) = recording()

            emailer.sendEmail(message())

            obs.assertObservedOperationWithValues(EmailKeys.MESSAGE_ID to "msg_123")
        }

    @Test
    fun `a transport error is thrown, observed, and recorded on the operation`() =
        runTest {
            val (emailer, obs, _) = recording(handler = { throw IOException("boom") })
            val details = message()

            assertFailsWith<IOException> { emailer.sendEmail(details) }

            // Even though the send failed, the fields were still observed and the failure recorded.
            obs.assertObservedOperationWithValues(
                EmailKeys.SUBJECT to details.subject,
                EmailKeys.TO_ADDRESS to details.toAddress,
            )
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `a non-2xx response throws a ResendApiException and records the failure`() =
        runTest {
            val (emailer, obs, _) = recording(handler = { HttpResponse(statusCode = 500) })
            val details = message()

            val error = assertFailsWith<ResendApiException> { emailer.sendEmail(details) }
            assertEquals(500, error.statusCode)

            obs.assertObservedOperationWithValues(
                EmailKeys.SUBJECT to details.subject,
                EmailKeys.TO_ADDRESS to details.toAddress,
            )
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `an open circuit rejects the send and issues no HTTP request`() =
        runTest {
            val breaker = RecordingCircuitBreaker(reject = true)
            val (emailer, _, fake) = recording(breaker = breaker)

            val error = assertFailsWith<Throwable> { emailer.sendEmail(message()) }
            assertEquals(ErrCircuitBroken, error)
            assertTrue(fake.requests.isEmpty())
            assertEquals(1, breaker.rejectionCount)
        }
}
