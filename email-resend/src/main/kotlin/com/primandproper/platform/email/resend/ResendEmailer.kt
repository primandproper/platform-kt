package com.primandproper.platform.email.resend

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.email.EmailKeys
import com.primandproper.platform.email.Emailer
import com.primandproper.platform.email.OutboundEmailMessage
import com.primandproper.platform.httpclient.HttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Component name for the Resend emailer's Observer. Mirrors platform-go's `resend.name`. */
internal const val NAME: String = "resend_emailer"

/** Lenient JSON: [encodeDefaults] so an empty body/subject still serializes; unknown response keys ignored. */
private val json =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

/** The Resend `POST /emails` request body. Port of the JSON the Resend SDK sends. */
@Serializable
private data class SendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String,
)

/** The Resend `POST /emails` response body; only the assigned message id is read. */
@Serializable
private data class SendEmailResponse(
    val id: String = "",
)

/** Thrown when an empty Resend API token is supplied. Port of platform-go's `resend.ErrEmptyAPIToken`. */
public class EmptyApiTokenException : IllegalArgumentException("empty Resend API token")

/**
 * Thrown when Resend answers a send with a non-2xx status. The analog of platform-go's SDK surfacing
 * a request error; [statusCode] carries the offending status for diagnostics.
 */
public class ResendApiException(
    public val statusCode: Int,
) : RuntimeException("resend request error: status $statusCode")

/**
 * Formats a `name <address>` header value the way Go's `net/mail.Address.String()` does — the port of
 * platform-go's `resend.formatAddress`.
 *
 * SECURITY (carries platform-go's C-08 fix): a blank name yields the bare [address], but any
 * non-blank [name] is emitted as an RFC 5322 quoted-string (wrapping in `"`, backslash-escaping `\`
 * and `"`). That is what stops a hostile display name like `x <evil@attacker.com>,` from breaking out
 * of the name and injecting a second recipient — the `<`, `>`, `@`, `,` all stay inside the quotes,
 * so the real address remains the only deliverable one.
 *
 * Note: unlike Go, a non-ASCII name is not RFC 2047 (`=?utf-8?…?=`) encoded here — a documented minor
 * divergence; the injection-defense property (always quoting a non-empty name) is preserved.
 */
internal fun formatAddress(
    name: String,
    address: String,
): String {
    if (name.isBlank()) return address
    val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\" <$address>"
}

/**
 * A Resend-backed [Emailer] — the port of platform-go's `resend.Emailer`, reimplemented over the
 * repo's [HttpClient] contract instead of the Resend Go SDK.
 *
 * [sendEmail] opens an Observer span, records the subject/to/from on both pillars (matching Go's
 * `op.Set(...)`), then runs the delivery under the injected [CircuitBreaker]. Delivery builds a JSON
 * `POST <baseUrl>/emails` carrying a `Bearer` token and the `{from, to[], subject, html}` body,
 * executes it via [HttpClient], and maps the outcome: a non-2xx status throws [ResendApiException],
 * and a successful response's message id is recorded on the span (`email.message_id`).
 *
 * Circuit-breaker semantics follow the `:analytics-segment` precedent: Go drives the primitive
 * `CannotProceed`/`Failed`/`Succeeded` quartet by hand, whereas this port folds the send into a
 * single `CircuitBreaker.execute` — an open breaker rejects with `ErrCircuitBroken` before any HTTP
 * request, and a failed send (transport error or non-2xx) counts as a breaker failure. Because the
 * subject/to/from are recorded before `execute`, they are observed even when the send fails (as
 * platform-go's `resend_test.go` asserts), and the thrown failure is recorded on the operation by the
 * enclosing `span` scope.
 *
 * TODO(metrics): platform-go increments send/error counters and a latency histogram per call. Those
 * are a metrics-pillar seam here (the span already records the operation); wire counters once the
 * observability metrics surface lands, matching how `:circuitbreaking`/`:analytics-segment` left it.
 */
public class ResendEmailer internal constructor(
    private val o11y: Observer,
    private val httpClient: HttpClient,
    private val circuitBreaker: CircuitBreaker,
    private val apiToken: String,
    private val sendUrl: String,
) : Emailer {
    override suspend fun sendEmail(details: OutboundEmailMessage): Unit =
        o11y.span("SendEmail") {
            set(EmailKeys.SUBJECT, details.subject)
            set(EmailKeys.TO_ADDRESS, details.toAddress)
            set(EmailKeys.FROM_ADDRESS, details.fromAddress)

            val messageId =
                circuitBreaker.execute {
                    val payload =
                        SendEmailRequest(
                            from = formatAddress(details.fromName, details.fromAddress),
                            to = listOf(formatAddress(details.toName, details.toAddress)),
                            subject = details.subject,
                            html = details.htmlContent,
                        )

                    val request =
                        HttpRequest.build {
                            method = HttpMethod.POST
                            url = sendUrl
                            header("Authorization", "Bearer $apiToken")
                            header("Content-Type", "application/json")
                            body(json.encodeToString(SendEmailRequest.serializer(), payload))
                        }

                    val response = httpClient.execute(request)
                    if (!response.isSuccessful) throw ResendApiException(response.statusCode)

                    runCatching {
                        json.decodeFromString(SendEmailResponse.serializer(), response.bodyAsText()).id
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                }

            if (messageId != null) set(EmailKeys.MESSAGE_ID, messageId)
        }
}

/**
 * Builds a production Resend-backed [Emailer].
 *
 * @param apiToken the Resend API token; an empty token throws [EmptyApiTokenException] (mirroring Go's
 *   `ErrEmptyAPIToken`).
 * @param httpClient the transport every send goes over (`:httpclient-okhttp` / `:httpclient-ktor` in
 *   production; a `FakeHttpClient` in tests).
 * @param baseUrl the API base; requests target `<baseUrl>/emails`. Defaults to Resend's real host.
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 * @param circuitBreaker optional breaker; defaults to the always-closed noop breaker.
 */
public fun ResendEmailer(
    apiToken: String,
    httpClient: HttpClient,
    baseUrl: String = ResendConfig.DEFAULT_BASE_URL,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
): ResendEmailer {
    if (apiToken.isEmpty()) throw EmptyApiTokenException()
    return ResendEmailer(
        o11y = Observer(NAME, logger, tracerProvider),
        httpClient = httpClient,
        circuitBreaker = circuitBreaker,
        apiToken = apiToken,
        sendUrl = baseUrl.trimEnd('/') + "/emails",
    )
}

/** Builds a production Resend-backed [Emailer] from a [ResendConfig]. Validates the token first. */
public fun ResendEmailer(
    config: ResendConfig,
    httpClient: HttpClient,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
): ResendEmailer {
    config.validate()
    return ResendEmailer(
        apiToken = config.apiToken,
        httpClient = httpClient,
        baseUrl = config.baseUrl,
        logger = logger,
        tracerProvider = tracerProvider,
        circuitBreaker = circuitBreaker,
    )
}
