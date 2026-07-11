package com.primandproper.platform.llm.anthropic

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.CircuitBrokenException
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.httpclient.FakeHttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.llm.CompletionChunk
import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.LlmKeys
import com.primandproper.platform.llm.Message
import com.primandproper.platform.llm.Role
import com.primandproper.platform.llm.TokenUsage
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Mirrors platform-go's `llm/anthropic/anthropic_test.go`, with `FakeHttpClient` standing in for `httptest`. */
class AnthropicLlmProviderTest {
    /** An Anthropic Messages API response carrying [text] as its single text block. */
    private fun messageResponse(text: String): String =
        """
        {
          "id": "msg-test",
          "type": "message",
          "role": "assistant",
          "model": "claude-opus-4-8",
          "content": [{"type": "text", "text": "$text"}],
          "stop_reason": "end_turn",
          "usage": {"input_tokens": 10, "output_tokens": 5}
        }
        """.trimIndent()

    /** A provider wired to a RecordingObserver and FakeHttpClient, so tests assert adaptation offline. */
    private fun recording(
        breaker: CircuitBreaker = NoopCircuitBreaker,
        defaultModel: String = AnthropicModels.DEFAULT,
        handler: suspend (HttpRequest) -> HttpResponse = {
            HttpResponse(statusCode = 200, body = messageResponse("Hello from Claude mock!").toByteArray())
        },
    ): Triple<AnthropicLlmProvider, RecordingObserver, FakeHttpClient> {
        val fake = FakeHttpClient(handler)
        val obs = RecordingObserver()
        val provider =
            AnthropicLlmProvider(
                o11y = obs,
                httpClient = fake,
                circuitBreaker = breaker,
                apiKey = "test-key",
                messagesUrl = "https://api.anthropic.com/v1/messages",
                defaultModel = defaultModel,
                maxTokens = 4096,
            )
        return Triple(provider, obs, fake)
    }

    private fun params(model: String = "claude-sonnet-5") = CompletionParams(model = model, messages = listOf(Message(Role.USER, "Hello")))

    // --- construction (ports TestNewProvider) ---

    @Test
    fun `factory with a valid key returns non-null`() {
        assertNotNull(AnthropicLlmProvider(apiKey = "test-key", httpClient = FakeHttpClient()))
    }

    @Test
    fun `factory with an empty key throws`() {
        assertFailsWith<EmptyApiKeyException> { AnthropicLlmProvider(apiKey = "", httpClient = FakeHttpClient()) }
    }

    @Test
    fun `config factory validates the key`() {
        assertFailsWith<EmptyApiKeyException> {
            AnthropicLlmProvider(config = AnthropicConfig(apiKey = ""), httpClient = FakeHttpClient())
        }
        assertNotNull(AnthropicLlmProvider(config = AnthropicConfig(apiKey = "k"), httpClient = FakeHttpClient()))
    }

    // --- complete (ports TestAnthropicProvider_Completion) ---

    @Test
    fun `complete parses the text content from the response`() =
        runTest {
            val (provider, _, _) = recording()

            val result = provider.complete(params())

            assertEquals("Hello from Claude mock!", result.content)
        }

    @Test
    fun `complete concatenates multiple text blocks`() =
        runTest {
            val body =
                """{"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world"}],"stop_reason":"end_turn"}"""
            val (provider, _, _) = recording(handler = { HttpResponse(statusCode = 200, body = body.toByteArray()) })

            assertEquals("Hello world", provider.complete(params()).content)
        }

    @Test
    fun `complete posts the correct request shape`() =
        runTest {
            val (provider, _, fake) = recording()

            provider.complete(params(model = "claude-sonnet-5"))

            val request = assertNotNull(fake.lastRequest)
            assertEquals(HttpMethod.POST, request.method)
            assertEquals("https://api.anthropic.com/v1/messages", request.url)
            assertEquals("test-key", request.headers.first("x-api-key"))
            assertEquals("2023-06-01", request.headers.first("anthropic-version"))
            assertEquals("application/json", request.headers.first("content-type"))

            val body = Json.parseToJsonElement(String(assertNotNull(request.body))).jsonObject
            assertEquals("claude-sonnet-5", body["model"]!!.jsonPrimitive.content)
            assertEquals(4096, body["max_tokens"]!!.jsonPrimitive.int)
            val messages = body["messages"]!!.jsonArray
            assertEquals(1, messages.size)
            assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("Hello", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        }

    @Test
    fun `complete observes the model and message count`() =
        runTest {
            val (provider, obs, _) = recording()

            provider.complete(params(model = "claude-sonnet-5"))

            obs.assertObservedOperationWithValues(
                LlmKeys.MODEL to "claude-sonnet-5",
                LlmKeys.MESSAGE_COUNT to 1,
            )
        }

    @Test
    fun `complete surfaces the usage and finish reason on the result`() =
        runTest {
            val (provider, _, _) = recording()

            val result = provider.complete(params())

            assertEquals(TokenUsage(inputTokens = 10, outputTokens = 5), result.usage)
            assertEquals(15, result.usage?.totalTokens)
            assertEquals("end_turn", result.finishReason)
        }

    @Test
    fun `stream emits a single terminal chunk carrying the reply usage and finish reason`() =
        runTest {
            val (provider, _, _) = recording()

            val chunks = provider.stream(params()).toList()

            assertEquals(
                listOf(
                    CompletionChunk(
                        content = "Hello from Claude mock!",
                        usage = TokenUsage(inputTokens = 10, outputTokens = 5),
                        finishReason = "end_turn",
                    ),
                ),
                chunks,
            )
        }

    @Test
    fun `complete records the token total and finish reason on success`() =
        runTest {
            val (provider, obs, _) = recording()

            provider.complete(params())

            obs.assertObservedOperationWithValues(
                LlmKeys.TOTAL_TOKENS to 15,
                LlmKeys.FINISH_REASON to "end_turn",
            )
        }

    @Test
    fun `complete falls back to the configured default model when the request model is blank`() =
        runTest {
            val (provider, obs, fake) = recording(defaultModel = "claude-haiku-4-5-20251001")

            provider.complete(CompletionParams(messages = listOf(Message(Role.USER, "Hi"))))

            val body = Json.parseToJsonElement(String(fake.lastRequest!!.body!!)).jsonObject
            assertEquals("claude-haiku-4-5-20251001", body["model"]!!.jsonPrimitive.content)
            obs.assertObservedOperationWithValues(LlmKeys.MODEL to "claude-haiku-4-5-20251001")
        }

    @Test
    fun `a non-2xx response throws AnthropicApiException and records the failure`() =
        runTest {
            val (provider, obs, _) =
                recording(handler = { HttpResponse(statusCode = 500, body = """{"error":{"message":"server error"}}""".toByteArray()) })

            val error = assertFailsWith<AnthropicApiException> { provider.complete(params()) }
            assertEquals(500, error.statusCode)

            // Even though the request failed, the model was still observed and the failure recorded.
            obs.assertObservedOperationWithValues(LlmKeys.MODEL to "claude-sonnet-5")
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `a transport error is thrown, observed, and recorded on the operation`() =
        runTest {
            val (provider, obs, _) = recording(handler = { throw IOException("boom") })

            assertFailsWith<IOException> { provider.complete(params()) }

            obs.assertObservedOperationWithValues(LlmKeys.MODEL to "claude-sonnet-5")
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `an open circuit rejects the completion and issues no HTTP request`() =
        runTest {
            val breaker = RecordingCircuitBreaker(reject = true)
            val (provider, _, fake) = recording(breaker = breaker)

            val error = assertFailsWith<Throwable> { provider.complete(params()) }
            assertTrue(error is CircuitBrokenException)
            assertTrue(fake.requests.isEmpty())
            assertEquals(1, breaker.rejectionCount)
        }

    @Test
    fun `the api key is never recorded on the span`() =
        runTest {
            val (provider, obs, _) = recording()

            provider.complete(params())

            // No observation on any pillar may carry the API key value.
            val leaked = obs.stream().any { it.value == "test-key" }
            assertTrue(!leaked, "API key must never be recorded on the span")
        }
}
