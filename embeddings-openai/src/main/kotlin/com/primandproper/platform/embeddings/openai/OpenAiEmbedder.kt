package com.primandproper.platform.embeddings.openai

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.ensureCircuitBreaker
import com.primandproper.platform.embeddings.Embedder
import com.primandproper.platform.embeddings.Embedding
import com.primandproper.platform.embeddings.EmbeddingInput
import com.primandproper.platform.embeddings.EmbeddingKeys
import com.primandproper.platform.httpclient.HttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** Component name for the OpenAI embedder's Observer. Mirrors platform-go's `openai.providerName`. */
internal const val NAME: String = "openai"

/** Lenient JSON: [encodeDefaults] so the fixed `encoding_format` still serializes; unknown response keys ignored. */
private val json =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

/** The OpenAI `POST /v1/embeddings` request body. Port of Go's `embeddingRequest`. */
@Serializable
private data class EmbeddingRequest(
    val input: String,
    val model: String,
    @SerialName("encoding_format") val encodingFormat: String = "float",
)

/** One datum of the OpenAI response; carries a single embedding vector. */
@Serializable
private data class EmbeddingDatum(
    val embedding: List<Float> = emptyList(),
)

/** The OpenAI `POST /v1/embeddings` response body; only the first datum's vector is read. Port of Go's `embeddingResponse`. */
@Serializable
private data class EmbeddingResponse(
    val data: List<EmbeddingDatum> = emptyList(),
)

/** Thrown when an empty OpenAI API key is supplied. Port of platform-go's `errors.New("...config is required")` guard on the key. */
public class EmptyApiKeyException : IllegalArgumentException("empty OpenAI API key")

/**
 * Thrown when OpenAI answers a generate with a non-2xx status. Port of platform-go's
 * `errors.Errorf("openai embedding API returned status %d ...")`; [statusCode] carries the offending
 * status for diagnostics.
 */
public class OpenAiApiException(
    public val statusCode: Int,
) : RuntimeException("openai embedding API returned status $statusCode")

/** Thrown when OpenAI answers 2xx but the `data` array is empty. Port of Go's `"...response contained no data"`. */
public class EmptyEmbeddingResponseException : RuntimeException("openai embedding response contained no data")

/**
 * An OpenAI-backed [Embedder] — the port of platform-go's `openai.embedder`, reimplemented over the
 * repo's [HttpClient] contract instead of a vendor SDK.
 *
 * [generateEmbedding] opens an Observer span, resolves the model (input override → config default →
 * `text-embedding-3-small`) and records it plus the content length on both pillars (matching Go's
 * `op.Set("embeddings.model", ...)` / `op.Set(keys.LengthKey, ...)`), then runs the request under the
 * injected [CircuitBreaker]. The request is a JSON `POST <baseUrl>/v1/embeddings` carrying a `Bearer`
 * token and `{input, model, encoding_format:"float"}`; the outcome is mapped as: a non-2xx status
 * throws [OpenAiApiException], an empty `data` array throws [EmptyEmbeddingResponseException], and a
 * successful vector's dimensionality is recorded on the span (`embedding.dimensions`).
 *
 * REDACTION: the API key never touches a span or log. It rides only in the outbound `Authorization`
 * header — the code `set`s only the model, length, and dimensions — so no credential can reach a
 * trace exporter or logger even on the error path.
 *
 * Circuit-breaker semantics follow the `:email-resend` precedent: the whole request is folded into a
 * single `CircuitBreaker.execute` — an open breaker rejects with `ErrCircuitBroken` before any HTTP
 * request, and a failed request (transport error, non-2xx, empty/malformed body) counts as a breaker
 * failure. Because the model/length are recorded before `execute`, they are observed even when the
 * request fails (as platform-go's `openai_test.go` asserts), and the thrown failure is recorded on
 * the operation by the enclosing `span` scope.
 *
 * Rate limiting (carried from Go's doc): this method does not retry. A non-200 (including 429) is
 * surfaced to the caller as [OpenAiApiException]; callers wanting backoff wrap this call (e.g. with
 * the `retry` package / an `HttpClientConfig.retryHook`).
 *
 * TODO(ollama): the Ollama backend is the same shape over `:httpclient-api` —
 * `POST http://localhost:11434/api/embed` with `{model, input}`, response `{embeddings:[[...]]}`
 * (the first row is the vector), default model `nomic-embed-text`, no API key.
 *
 * TODO(cohere): likewise — `POST https://api.cohere.com/v2/embed` with
 * `{texts:[input], model, input_type:"search_document", embedding_types:["float"]}`, response
 * `{embeddings:{float:[[...]]}}`, default model `embed-english-v3.0`, Bearer key.
 */
public class OpenAiEmbedder internal constructor(
    private val o11y: Observer,
    private val httpClient: HttpClient,
    private val circuitBreaker: CircuitBreaker,
    private val apiKey: String,
    private val defaultModel: String,
    private val embeddingsUrl: String,
) : Embedder {
    override suspend fun generateEmbedding(input: EmbeddingInput): Embedding =
        o11y.span("GenerateEmbedding") {
            val model = input.model.ifEmpty { defaultModel }.ifEmpty { OpenAiConfig.DEFAULT_MODEL }
            set(EmbeddingKeys.MODEL, model)
            set(Keys.LENGTH, input.content.length)

            val vector =
                circuitBreaker.execute {
                    val payload = EmbeddingRequest(input = input.content, model = model)

                    val request =
                        HttpRequest.build {
                            method = HttpMethod.POST
                            url = embeddingsUrl
                            header("Authorization", "Bearer $apiKey")
                            header("Content-Type", "application/json")
                            body(json.encodeToString(EmbeddingRequest.serializer(), payload))
                        }

                    val response = httpClient.execute(request)
                    if (!response.isSuccessful) throw OpenAiApiException(response.statusCode)

                    val parsed = json.decodeFromString(EmbeddingResponse.serializer(), response.bodyAsText())
                    val datum = parsed.data.firstOrNull() ?: throw EmptyEmbeddingResponseException()
                    datum.embedding
                }

            set(EmbeddingKeys.DIMENSIONS, vector.size)

            Embedding(
                vector = vector,
                sourceText = input.content,
                model = model,
                provider = NAME,
                dimensions = vector.size,
                generatedAt = Instant.now(),
            )
        }
}

/**
 * Builds a production OpenAI-backed [Embedder].
 *
 * @param apiKey the OpenAI API key; an empty key throws [EmptyApiKeyException] (mirroring Go's
 *   required-key guard).
 * @param httpClient the transport every request goes over (`:httpclient-okhttp` / `:httpclient-ktor`
 *   in production; a `FakeHttpClient` in tests).
 * @param baseUrl the API base; requests target `<baseUrl>/v1/embeddings`. Defaults to OpenAI's host.
 * @param defaultModel the model used when an [EmbeddingInput] does not name one; empty falls back to
 *   [OpenAiConfig.DEFAULT_MODEL].
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 * @param circuitBreaker optional breaker; defaults to the always-closed noop breaker.
 */
public fun OpenAiEmbedder(
    apiKey: String,
    httpClient: HttpClient,
    baseUrl: String = OpenAiConfig.DEFAULT_BASE_URL,
    defaultModel: String = "",
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    circuitBreaker: CircuitBreaker? = null,
): OpenAiEmbedder {
    if (apiKey.isEmpty()) throw EmptyApiKeyException()
    return OpenAiEmbedder(
        o11y = Observer(NAME, logger, tracerProvider),
        httpClient = httpClient,
        circuitBreaker = ensureCircuitBreaker(circuitBreaker),
        apiKey = apiKey,
        defaultModel = defaultModel,
        embeddingsUrl = baseUrl.trimEnd('/') + "/v1/embeddings",
    )
}

/** Builds a production OpenAI-backed [Embedder] from an [OpenAiConfig]. Validates the key first. */
public fun OpenAiEmbedder(
    config: OpenAiConfig,
    httpClient: HttpClient,
    logger: Logger? = null,
    tracerProvider: TracerProvider? = null,
    circuitBreaker: CircuitBreaker? = null,
): OpenAiEmbedder {
    config.validate()
    return OpenAiEmbedder(
        apiKey = config.apiKey,
        httpClient = httpClient,
        baseUrl = config.baseUrl,
        defaultModel = config.defaultModel,
        logger = logger,
        tracerProvider = tracerProvider,
        circuitBreaker = circuitBreaker,
    )
}
