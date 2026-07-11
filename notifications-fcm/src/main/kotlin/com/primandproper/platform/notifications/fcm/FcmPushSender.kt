package com.primandproper.platform.notifications.fcm

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.httpclient.HttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.notifications.NotificationKeys
import com.primandproper.platform.notifications.Platform
import com.primandproper.platform.notifications.PlatformNotSupportedException
import com.primandproper.platform.notifications.PushMessage
import com.primandproper.platform.notifications.PushNotificationSender
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.Operation
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Component name for the FCM sender's Observer. Mirrors platform-go's `fcm.o11yName`. */
internal const val NAME: String = "android_notif_sender"

/** Lenient JSON: [encodeDefaults] off so absent optional fields (token/topic/data) are omitted; unknown response keys ignored. */
private val json =
    Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

/** The FCM HTTP v1 `messages:send` request envelope. */
@Serializable
private data class FcmSendRequest(
    val message: FcmMessage,
)

/**
 * The FCM `message` object. Exactly one of [token] / [topic] targets the send; [data] is omitted when
 * empty. Port of the JSON the Firebase SDK builds from a `messaging.Message`.
 */
@Serializable
private data class FcmMessage(
    val notification: FcmNotification,
    val token: String? = null,
    val topic: String? = null,
    val data: Map<String, String>? = null,
)

/** The visible alert. */
@Serializable
private data class FcmNotification(
    val title: String,
    val body: String,
)

/** The success body; `name` is the assigned message resource (`projects/<id>/messages/<message-id>`). */
@Serializable
private data class FcmSendResponse(
    val name: String = "",
)

/** The error envelope FCM returns on a non-2xx. */
@Serializable
private data class FcmErrorResponse(
    val error: FcmErrorBody? = null,
)

@Serializable
private data class FcmErrorBody(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
)

/**
 * Thrown when FCM answers a send with a non-2xx status. The analog of platform-go's SDK surfacing a
 * request error; [statusCode] carries the HTTP status and [fcmStatus] the FCM error code
 * (`UNAUTHENTICATED`, `INVALID_ARGUMENT`, …) for diagnostics.
 */
public class FcmApiException(
    public val statusCode: Int,
    public val fcmStatus: String = "",
    detail: String = "",
) : RuntimeException(
        "fcm request error: status $statusCode" +
            (if (fcmStatus.isNotEmpty()) " ($fcmStatus)" else "") +
            (if (detail.isNotEmpty()) ": $detail" else ""),
    )

/**
 * An FCM-backed [PushNotificationSender] — the port of platform-go's `fcm.Sender`, reimplemented over
 * the repo's [HttpClient] contract (FCM HTTP v1) instead of the Firebase SDK.
 *
 * DIRECTION: this is the SERVER send side. A server calls [sendPush]/[sendToTopic] to push to Android
 * devices; the device-side RECEIVE half lives in the direction-flipped `:notifications-android`.
 *
 * Each send opens an Observer span, records the platform/title (never the device token — see
 * [NotificationKeys]'s redaction note), then runs the delivery under the injected [CircuitBreaker].
 * Delivery mints a fresh `Bearer` token from [tokenProvider], builds a JSON
 * `POST <baseUrl>/v1/projects/<projectId>/messages:send` carrying the `{message:{token|topic,
 * notification, data}}` body, executes it via [HttpClient], and maps the outcome: a non-2xx status
 * throws [FcmApiException] (parsed from FCM's error envelope), and a successful response's message
 * name is recorded on the span (`notification.message_id`).
 *
 * Circuit-breaker semantics follow the `:email-resend` precedent: the send folds into a single
 * `CircuitBreaker.execute` — an open breaker rejects with `ErrCircuitBroken` before any HTTP request,
 * and a failed send (transport error or non-2xx) counts as a breaker failure. Because the
 * platform/title are recorded before `execute`, they are observed even when the send fails, and the
 * thrown failure is recorded on the operation by the enclosing `span` scope (mirroring platform-go's
 * `fcm_sender_test.go`, which asserts the title is observed and the operation carries one error).
 *
 * PLATFORM ROUTING: platform-go's `MultiPlatformPushSender` routes `ios` to APNs and `android` to FCM.
 * This module implements only the FCM half, so a non-Android platform throws
 * [PlatformNotSupportedException] — APNs is a documented `TODO(apns)` seam that would drop in behind
 * the same interface. A non-null [PushMessage.badgeCount] is dropped on this path (Android has no
 * OS-level FCM badge), and the drop is logged, exactly as platform-go does.
 *
 * TODO(metrics): platform-go increments send/error `Int64Counter`s per call. Those are a
 * metrics-pillar seam here (the span already records the operation); wire counters once the
 * observability metrics surface lands, matching how `:email-resend`/`:analytics-segment` left it.
 */
public class FcmPushSender internal constructor(
    private val o11y: Observer,
    private val httpClient: HttpClient,
    private val circuitBreaker: CircuitBreaker,
    private val projectId: String,
    private val tokenProvider: suspend () -> String,
    baseUrl: String,
) : PushNotificationSender {
    private val sendUrl: String = baseUrl.trimEnd('/') + "/v1/projects/" + projectId + "/messages:send"

    override suspend fun sendPush(
        platform: Platform,
        token: String,
        message: PushMessage,
    ): Unit =
        o11y.span("SendPush") {
            set(NotificationKeys.PLATFORM, platform.wireName)
            set(NotificationKeys.TITLE, message.title)
            requireAndroid(platform)
            dropBadgeIfPresent(message)
            val name =
                deliver(
                    FcmMessage(
                        notification = FcmNotification(message.title, message.body),
                        token = token,
                        data = message.data.ifEmpty { null },
                    ),
                )
            if (name != null) set(NotificationKeys.MESSAGE_ID, name)
        }

    override suspend fun sendToTopic(
        platform: Platform,
        topic: String,
        message: PushMessage,
    ): Unit =
        o11y.span("SendToTopic") {
            set(NotificationKeys.PLATFORM, platform.wireName)
            set(NotificationKeys.TOPIC, topic)
            set(NotificationKeys.TITLE, message.title)
            requireAndroid(platform)
            dropBadgeIfPresent(message)
            val name =
                deliver(
                    FcmMessage(
                        notification = FcmNotification(message.title, message.body),
                        topic = topic,
                        data = message.data.ifEmpty { null },
                    ),
                )
            if (name != null) set(NotificationKeys.MESSAGE_ID, name)
        }

    /** Runs the HTTP send under the breaker; returns the assigned message name, or null if absent. */
    private suspend fun deliver(message: FcmMessage): String? =
        circuitBreaker.execute {
            val request =
                HttpRequest.build {
                    method = HttpMethod.POST
                    url = sendUrl
                    header("Authorization", "Bearer ${tokenProvider()}")
                    header("Content-Type", "application/json")
                    body(json.encodeToString(FcmSendRequest.serializer(), FcmSendRequest(message)))
                }

            val response = httpClient.execute(request)
            if (!response.isSuccessful) throw parseError(response)

            runCatching {
                json.decodeFromString(FcmSendResponse.serializer(), response.bodyAsText()).name
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }

    private companion object {
        fun requireAndroid(platform: Platform) {
            if (platform != Platform.ANDROID) throw PlatformNotSupportedException(platform)
        }

        fun parseError(response: HttpResponse): FcmApiException {
            val body = runCatching { json.decodeFromString(FcmErrorResponse.serializer(), response.bodyAsText()).error }.getOrNull()
            return FcmApiException(response.statusCode, body?.status ?: "", body?.message ?: "")
        }
    }

    private fun Operation.dropBadgeIfPresent(message: PushMessage) {
        if (message.badgeCount != null) {
            logger.info("dropping badgeCount: unsupported on the FCM/Android path")
        }
    }
}

/**
 * Builds a production FCM-backed sender that mints its `Bearer` token from [tokenProvider] on each
 * send — the shape for the refreshing OAuth2 case (see the module's `TODO(fcm-oauth)`).
 *
 * @param projectId the Firebase project id; an empty id throws [EmptyFcmProjectIdException].
 * @param httpClient the transport every send goes over (a `FakeHttpClient` in tests).
 * @param tokenProvider supplies a fresh OAuth2 access token per send.
 * @param baseUrl the API base; requests target `<baseUrl>/v1/projects/<projectId>/messages:send`.
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 * @param circuitBreaker optional breaker; defaults to the always-closed noop breaker.
 */
public fun FcmPushSender(
    projectId: String,
    httpClient: HttpClient,
    tokenProvider: suspend () -> String,
    baseUrl: String = FcmConfig.DEFAULT_BASE_URL,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
): FcmPushSender {
    if (projectId.isEmpty()) throw EmptyFcmProjectIdException()
    return FcmPushSender(
        o11y = Observer(NAME, logger, tracerProvider),
        httpClient = httpClient,
        circuitBreaker = circuitBreaker,
        projectId = projectId,
        tokenProvider = tokenProvider,
        baseUrl = baseUrl,
    )
}

/** Builds a production FCM-backed sender from a [FcmConfig], using its already-minted static token. */
public fun FcmPushSender(
    config: FcmConfig,
    httpClient: HttpClient,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
): FcmPushSender {
    config.validate()
    return FcmPushSender(
        projectId = config.projectId,
        httpClient = httpClient,
        tokenProvider = { config.accessToken },
        baseUrl = config.baseUrl,
        logger = logger,
        tracerProvider = tracerProvider,
        circuitBreaker = circuitBreaker,
    )
}
