package com.primandproper.platform.notifications.fcm

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.ErrCircuitBroken
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.httpclient.FakeHttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.notifications.NotificationKeys
import com.primandproper.platform.notifications.PLATFORM_ANDROID
import com.primandproper.platform.notifications.PLATFORM_IOS
import com.primandproper.platform.notifications.PlatformNotSupportedException
import com.primandproper.platform.notifications.PushMessage
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors platform-go's `fcm_sender_test.go`, with `FakeHttpClient` standing in for the SDK's HTTP transport. */
class FcmPushSenderTest {
    private val okBody = """{"name":"projects/test-project/messages/12345"}"""

    /** A sender wired to a RecordingObserver and FakeHttpClient, so tests assert adaptation offline. */
    private fun recording(
        breaker: CircuitBreaker = NoopCircuitBreaker,
        handler: suspend (HttpRequest) -> HttpResponse = { HttpResponse(statusCode = 200, body = okBody.toByteArray()) },
    ): Triple<FcmPushSender, RecordingObserver, FakeHttpClient> {
        val fake = FakeHttpClient(handler)
        val obs = RecordingObserver()
        val sender =
            FcmPushSender(
                o11y = obs,
                httpClient = fake,
                circuitBreaker = breaker,
                projectId = "test-project",
                tokenProvider = { "access-token" },
                baseUrl = "https://fcm.googleapis.com",
            )
        return Triple(sender, obs, fake)
    }

    private fun message() =
        PushMessage(
            title = "Test Title",
            body = "Test Body",
            data = mapOf("deepLink" to "app://home"),
        )

    // --- construction ---

    @Test
    fun `factory with a valid project id returns non-null`() {
        assertNotNull(FcmPushSender(projectId = "p", httpClient = FakeHttpClient(), tokenProvider = { "t" }))
    }

    @Test
    fun `factory with an empty project id throws`() {
        assertFailsWith<EmptyFcmProjectIdException> {
            FcmPushSender(projectId = "", httpClient = FakeHttpClient(), tokenProvider = { "t" })
        }
    }

    @Test
    fun `config factory validates project id and token`() {
        assertFailsWith<EmptyFcmProjectIdException> {
            FcmPushSender(config = FcmConfig(projectId = "", accessToken = "t"), httpClient = FakeHttpClient())
        }
        assertFailsWith<EmptyFcmAccessTokenException> {
            FcmPushSender(config = FcmConfig(projectId = "p", accessToken = ""), httpClient = FakeHttpClient())
        }
        assertNotNull(FcmPushSender(config = FcmConfig(projectId = "p", accessToken = "t"), httpClient = FakeHttpClient()))
    }

    // --- request shape ---

    @Test
    fun `sendPush posts the correct FCM HTTP v1 request shape`() =
        runTest {
            val (sender, _, fake) = recording()

            sender.sendPush(PLATFORM_ANDROID, "device-token-abc", message())

            val request = assertNotNull(fake.lastRequest)
            assertEquals(HttpMethod.POST, request.method)
            assertEquals("https://fcm.googleapis.com/v1/projects/test-project/messages:send", request.url)
            assertEquals("Bearer access-token", request.headers.first("authorization"))
            assertEquals("application/json", request.headers.first("content-type"))

            val msg = Json.parseToJsonElement(String(assertNotNull(request.body))).jsonObject["message"]!!.jsonObject
            assertEquals("device-token-abc", msg["token"]!!.jsonPrimitive.content)
            assertNull(msg["topic"])
            val notification = msg["notification"]!!.jsonObject
            assertEquals("Test Title", notification["title"]!!.jsonPrimitive.content)
            assertEquals("Test Body", notification["body"]!!.jsonPrimitive.content)
            assertEquals("app://home", msg["data"]!!.jsonObject["deepLink"]!!.jsonPrimitive.content)
        }

    @Test
    fun `sendToTopic targets a topic instead of a token`() =
        runTest {
            val (sender, _, fake) = recording()

            sender.sendToTopic(PLATFORM_ANDROID, "weather", message())

            val msg = Json.parseToJsonElement(String(assertNotNull(fake.lastRequest!!.body))).jsonObject["message"]!!.jsonObject
            assertEquals("weather", msg["topic"]!!.jsonPrimitive.content)
            assertNull(msg["token"])
        }

    // --- observability ---

    @Test
    fun `sendPush observes the platform and title but never the device token`() =
        runTest {
            val (sender, obs, _) = recording()

            sender.sendPush(PLATFORM_ANDROID, "secret-device-token", message())

            obs.assertObservedOperationWithValues(
                NotificationKeys.PLATFORM to PLATFORM_ANDROID,
                NotificationKeys.TITLE to "Test Title",
            )
            // The credential-bearing device token must never be recorded on the span.
            val recorded = obs.operations.single().values.values.map { it.toString() }
            assertTrue(recorded.none { it.contains("secret-device-token") })
        }

    @Test
    fun `sendPush records the returned message name on the span`() =
        runTest {
            val (sender, obs, _) = recording()

            sender.sendPush(PLATFORM_ANDROID, "t", message())

            obs.assertObservedOperationWithValues(
                NotificationKeys.MESSAGE_ID to "projects/test-project/messages/12345",
            )
        }

    // --- error handling ---

    @Test
    fun `a non-2xx response throws FcmApiException parsed from the error envelope and records the failure`() =
        runTest {
            val errorBody = """{"error":{"code":401,"message":"unauthorized","status":"UNAUTHENTICATED"}}"""
            val (sender, obs, _) = recording(handler = { HttpResponse(statusCode = 401, body = errorBody.toByteArray()) })

            val error = assertFailsWith<FcmApiException> { sender.sendPush(PLATFORM_ANDROID, "t", message()) }
            assertEquals(401, error.statusCode)
            assertEquals("UNAUTHENTICATED", error.fcmStatus)

            obs.assertObservedOperationWithValues(NotificationKeys.TITLE to "Test Title")
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `a transport error is thrown, observed, and recorded on the operation`() =
        runTest {
            val (sender, obs, _) = recording(handler = { throw IOException("boom") })

            assertFailsWith<IOException> { sender.sendPush(PLATFORM_ANDROID, "t", message()) }

            obs.assertObservedOperationWithValues(NotificationKeys.TITLE to "Test Title")
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `a non-android platform is rejected and issues no HTTP request`() =
        runTest {
            val (sender, obs, fake) = recording()

            val error = assertFailsWith<PlatformNotSupportedException> { sender.sendPush(PLATFORM_IOS, "t", message()) }
            assertEquals(PLATFORM_IOS, error.platform)
            assertTrue(fake.requests.isEmpty())
            assertEquals(1, obs.operations.single().errors.size)
        }

    // --- circuit breaking ---

    @Test
    fun `an open circuit rejects the send and issues no HTTP request`() =
        runTest {
            val breaker = RecordingCircuitBreaker(reject = true)
            val (sender, _, fake) = recording(breaker = breaker)

            val error = assertFailsWith<Throwable> { sender.sendPush(PLATFORM_ANDROID, "t", message()) }
            assertEquals(ErrCircuitBroken, error)
            assertTrue(fake.requests.isEmpty())
            assertEquals(1, breaker.rejectionCount)
        }

    @Test
    fun `a failed send counts as a breaker failure`() =
        runTest {
            val breaker = RecordingCircuitBreaker()
            val (sender, _, _) = recording(breaker = breaker, handler = { HttpResponse(statusCode = 500) })

            assertFailsWith<FcmApiException> { sender.sendPush(PLATFORM_ANDROID, "t", message()) }
            assertEquals(1, breaker.failureCount)
        }
}
