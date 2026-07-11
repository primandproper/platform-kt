package com.primandproper.platform.embeddings.openai

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.CircuitBrokenException
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.circuitbreaking.RecordingCircuitBreaker
import com.primandproper.platform.embeddings.EmbeddingInput
import com.primandproper.platform.embeddings.EmbeddingKeys
import com.primandproper.platform.httpclient.FakeHttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.httpclient.HttpResponse
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Mirrors platform-go's `embeddings/openai/openai_test.go`, with `FakeHttpClient` standing in for `httptest`. */
class OpenAiEmbedderTest {
    private val okBody =
        """{"object":"list","data":[{"object":"embedding","index":0,""" +
            """"embedding":[0.1,0.2,0.3]}],"model":"text-embedding-3-small"}"""

    /** An embedder wired to a RecordingObserver and FakeHttpClient, so tests assert adaptation offline. */
    private fun recording(
        defaultModel: String = "",
        breaker: CircuitBreaker = NoopCircuitBreaker,
        handler: suspend (HttpRequest) -> HttpResponse = { HttpResponse(statusCode = 200, body = okBody.toByteArray()) },
    ): Triple<OpenAiEmbedder, RecordingObserver, FakeHttpClient> {
        val fake = FakeHttpClient(handler)
        val obs = RecordingObserver()
        val embedder =
            OpenAiEmbedder(
                o11y = obs,
                httpClient = fake,
                circuitBreaker = breaker,
                apiKey = "test-key",
                defaultModel = defaultModel,
                embeddingsUrl = "https://api.openai.com/v1/embeddings",
            )
        return Triple(embedder, obs, fake)
    }

    // --- construction (ports TestNewEmbedder) ---

    @Test
    fun `factory with a valid key returns non-null`() {
        assertNotNull(OpenAiEmbedder(apiKey = "test-key", httpClient = FakeHttpClient()))
    }

    @Test
    fun `factory with an empty key throws`() {
        assertFailsWith<EmptyApiKeyException> { OpenAiEmbedder(apiKey = "", httpClient = FakeHttpClient()) }
    }

    @Test
    fun `config factory validates the key`() {
        assertFailsWith<EmptyApiKeyException> {
            OpenAiEmbedder(config = OpenAiConfig(apiKey = ""), httpClient = FakeHttpClient())
        }
        assertNotNull(OpenAiEmbedder(config = OpenAiConfig(apiKey = "k"), httpClient = FakeHttpClient()))
    }

    // --- GenerateEmbedding (ports TestEmbedder_GenerateEmbedding) ---

    @Test
    fun `generateEmbedding parses the response into a vector with provenance`() =
        runTest {
            val (embedder, obs, _) = recording()

            val result = embedder.generateEmbedding(EmbeddingInput(content = "hello world"))

            assertEquals("hello world", result.sourceText)
            assertEquals("text-embedding-3-small", result.model)
            assertEquals("openai", result.provider)
            assertEquals(3, result.dimensions)
            assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f), result.vector)
            assertNotNull(result.generatedAt)

            obs.assertObservedOperationWithValues(
                EmbeddingKeys.MODEL to "text-embedding-3-small",
                Keys.LENGTH to "hello world".length,
                EmbeddingKeys.DIMENSIONS to 3,
            )
        }

    @Test
    fun `generateEmbedding posts the correct request shape`() =
        runTest {
            val (embedder, _, fake) = recording()

            embedder.generateEmbedding(EmbeddingInput(content = "hello world"))

            val request = assertNotNull(fake.lastRequest)
            assertEquals(HttpMethod.POST, request.method)
            assertEquals("https://api.openai.com/v1/embeddings", request.url)
            assertEquals("Bearer test-key", request.headers.first("authorization"))
            assertEquals("application/json", request.headers.first("content-type"))

            val body = Json.parseToJsonElement(String(assertNotNull(request.body))).jsonObject
            assertEquals("hello world", body["input"]!!.jsonPrimitive.content)
            assertEquals("text-embedding-3-small", body["model"]!!.jsonPrimitive.content)
            assertEquals("float", body["encoding_format"]!!.jsonPrimitive.content)
        }

    @Test
    fun `generateEmbedding uses the input model override`() =
        runTest {
            val (embedder, _, fake) = recording(defaultModel = "text-embedding-3-small")

            val result =
                embedder.generateEmbedding(
                    EmbeddingInput(content = "hello", model = "text-embedding-3-large"),
                )

            assertEquals("text-embedding-3-large", result.model)
            val body = Json.parseToJsonElement(String(fake.lastRequest!!.body!!)).jsonObject
            assertEquals("text-embedding-3-large", body["model"]!!.jsonPrimitive.content)
        }

    @Test
    fun `generateEmbedding falls back to the config default model`() =
        runTest {
            val (embedder, _, fake) = recording(defaultModel = "text-embedding-3-large")

            val result = embedder.generateEmbedding(EmbeddingInput(content = "hello"))

            assertEquals("text-embedding-3-large", result.model)
            val body = Json.parseToJsonElement(String(fake.lastRequest!!.body!!)).jsonObject
            assertEquals("text-embedding-3-large", body["model"]!!.jsonPrimitive.content)
        }

    @Test
    fun `the factory targets the default base URL`() =
        runTest {
            val fake = FakeHttpClient { HttpResponse(statusCode = 200, body = okBody.toByteArray()) }
            val embedder = OpenAiEmbedder(apiKey = "test-key", httpClient = fake)

            embedder.generateEmbedding(EmbeddingInput(content = "hello"))

            assertTrue(fake.lastRequest!!.url.startsWith(OpenAiConfig.DEFAULT_BASE_URL))
            assertEquals("https://api.openai.com/v1/embeddings", fake.lastRequest!!.url)
        }

    @Test
    fun `a non-2xx response throws OpenAiApiException, is observed, and is recorded on the operation`() =
        runTest {
            val (embedder, obs, _) = recording(handler = { HttpResponse(statusCode = 500) })

            val error = assertFailsWith<OpenAiApiException> { embedder.generateEmbedding(EmbeddingInput(content = "hello")) }
            assertEquals(500, error.statusCode)

            // Even though the request failed, the model/length were still observed and the failure recorded.
            obs.assertObservedOperationWithValues(
                EmbeddingKeys.MODEL to "text-embedding-3-small",
                Keys.LENGTH to "hello".length,
            )
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `a malformed JSON response throws and is recorded`() =
        runTest {
            val (embedder, obs, _) = recording(handler = { HttpResponse(statusCode = 200, body = "{not json".toByteArray()) })

            assertFailsWith<Exception> { embedder.generateEmbedding(EmbeddingInput(content = "hello")) }
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `an empty data response throws EmptyEmbeddingResponseException`() =
        runTest {
            val (embedder, obs, _) = recording(handler = { HttpResponse(statusCode = 200, body = """{"data":[]}""".toByteArray()) })

            assertFailsWith<EmptyEmbeddingResponseException> { embedder.generateEmbedding(EmbeddingInput(content = "hello")) }
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `a transport error is thrown, observed, and recorded on the operation`() =
        runTest {
            val (embedder, obs, _) = recording(handler = { throw IOException("boom") })

            assertFailsWith<IOException> { embedder.generateEmbedding(EmbeddingInput(content = "hello")) }

            obs.assertObservedOperationWithValues(EmbeddingKeys.MODEL to "text-embedding-3-small")
            assertEquals(1, obs.operations.single().errors.size)
        }

    @Test
    fun `an open circuit rejects the request and issues no HTTP call`() =
        runTest {
            val breaker = RecordingCircuitBreaker(reject = true)
            val (embedder, _, fake) = recording(breaker = breaker)

            val error = assertFailsWith<Throwable> { embedder.generateEmbedding(EmbeddingInput(content = "hello")) }
            assertTrue(error is CircuitBrokenException)
            assertTrue(fake.requests.isEmpty())
            assertEquals(1, breaker.rejectionCount)
        }
}
