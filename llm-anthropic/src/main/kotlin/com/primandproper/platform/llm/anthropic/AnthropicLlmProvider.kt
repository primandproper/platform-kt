package com.primandproper.platform.llm.anthropic

import com.primandproper.platform.circuitbreaking.CircuitBreaker
import com.primandproper.platform.circuitbreaking.NoopCircuitBreaker
import com.primandproper.platform.httpclient.HttpClient
import com.primandproper.platform.httpclient.HttpMethod
import com.primandproper.platform.httpclient.HttpRequest
import com.primandproper.platform.llm.CompletionParams
import com.primandproper.platform.llm.CompletionResult
import com.primandproper.platform.llm.LlmKeys
import com.primandproper.platform.llm.LlmProvider
import com.primandproper.platform.llm.Role
import com.primandproper.platform.llm.TokenUsage
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.observability.span
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Component name for the Anthropic provider's Observer. Mirrors platform-go's `anthropic.name`. */
internal const val NAME: String = "anthropic_llm"

/** The Anthropic API version header value the Messages endpoint requires. */
internal const val ANTHROPIC_VERSION: String = "2023-06-01"

/** Lenient JSON: [encodeDefaults] so `max_tokens` always serializes; unknown response keys ignored. */
private val json =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

/** One message in the Anthropic Messages `POST /v1/messages` request body. */
@Serializable
private data class ApiMessage(
    val role: String,
    val content: String,
)

/**
 * The Anthropic Messages `POST /v1/messages` request body: `{model, max_tokens, system?, messages[]}`.
 *
 * [system] carries the hoisted system prompt (Anthropic's Messages API only accepts `user`/`assistant`
 * in [messages]; system prompts live in this top-level field). It is annotated
 * [EncodeDefault.Mode.NEVER] so that — despite the encoder's `encodeDefaults = true` (needed for
 * `max_tokens`) — a `null` system is omitted from the wire rather than serialized as `"system": null`.
 */
@OptIn(ExperimentalSerializationApi::class) // for [EncodeDefault] on [system]
@Serializable
private data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ApiMessage>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val system: String? = null,
)

/** One content block in the response; only `text` blocks carry the assistant's reply. */
@Serializable
private data class ContentBlock(
    val type: String = "",
    val text: String = "",
)

/** The token accounting Anthropic reports; summed into [LlmKeys.TOTAL_TOKENS]. */
@Serializable
private data class Usage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

/** The Anthropic Messages response body; the `text` blocks are concatenated into the result. */
@Serializable
private data class MessagesResponse(
    val content: List<ContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: Usage? = null,
)

/**
 * Thrown when Anthropic answers a completion with a non-2xx status. The analog of platform-go's
 * underlying library surfacing a request error; [statusCode] carries the offending status for
 * diagnostics. The response body is intentionally not attached (it can echo request content).
 */
public class AnthropicApiException(
    public val statusCode: Int,
) : RuntimeException("anthropic request error: status $statusCode")

/**
 * Thrown when a completion carries a [Role.TOOL] message. Anthropic represents tool results as
 * `tool_result` content blocks that must reference, by id, the `tool_use` block they answer — data the
 * shared [com.primandproper.platform.llm.Message] model (a flat [Role] + `content` string) does not
 * carry. Rather than emit an invalid `role: "tool"` into `messages[]` (which Anthropic rejects with
 * HTTP 400), the provider fails fast here with the offending [role] attached for diagnostics.
 */
public class UnsupportedRoleException(
    public val role: Role,
) : IllegalArgumentException("anthropic messages[] does not support role: ${role.value}")

/**
 * An Anthropic-backed [LlmProvider] — the port of platform-go's `anthropic` provider, reimplemented
 * over the repo's [HttpClient] contract instead of an Anthropic SDK.
 *
 * [complete] opens an Observer span, resolves the model (request model, else the configured default,
 * else [AnthropicModels.DEFAULT] — the same double fallback Go performs), records the model and
 * message count on both pillars, then runs the request under the injected [CircuitBreaker]. The
 * request is a JSON `POST <baseUrl>/v1/messages` carrying the `x-api-key` and `anthropic-version`
 * headers and a `{model, max_tokens, system?, messages:[{role,content}]}` body — `Role.SYSTEM`
 * messages are hoisted into the top-level `system` field, only `user`/`assistant` remain in
 * `messages[]`, and a `Role.TOOL` message throws [UnsupportedRoleException]; a non-2xx status throws
 * [AnthropicApiException]. On success the `text` content blocks are concatenated into the
 * [CompletionResult], and the reported token total (`llm.tokens.total`) and stop reason
 * (`llm.finish_reason`) are recorded on the span, and both are also surfaced to the caller on the
 * returned [CompletionResult] (`usage`/`finishReason`).
 *
 * Streaming: this backend does not yet consume Anthropic's SSE stream, so it inherits
 * [LlmProvider.stream]'s default — one terminal chunk carrying the whole [complete] reply plus its
 * usage/finish-reason. Wiring `stream: true` and parsing `text/event-stream` is a `TODO(streaming)`
 * seam once the [HttpClient] contract exposes a streaming response body.
 *
 * SECURITY: the API key is sent only as the `x-api-key` request header — which the httpclient span
 * integration redacts before recording — and is never attached as a span attribute or logged. No
 * request/response *content* is recorded either; only the low-cardinality [LlmKeys] metadata is.
 *
 * Circuit-breaker semantics follow the `:email-resend`/`:analytics-segment` precedent: the request is
 * folded into a single `CircuitBreaker.execute`, so an open breaker rejects with `ErrCircuitBroken`
 * before any HTTP request and a failed request (transport error or non-2xx) counts as a breaker
 * failure. Because the model/message-count are recorded before `execute`, they are observed even when
 * the request fails (as platform-go's `anthropic_test.go` asserts), and the thrown failure is
 * recorded on the operation by the enclosing `span` scope.
 *
 * Rate limiting (carrying platform-go's note): this method does not retry. A 429 surfaces to the
 * caller as an [AnthropicApiException]; callers wanting backoff should wrap the call with the
 * platform's retry seam.
 *
 * TODO(metrics): platform-go increments request/error counters and a latency histogram per call.
 * Those are a metrics-pillar seam here (the span already records the operation); wire counters once
 * the observability metrics surface lands, matching how `:email-resend`/`:circuitbreaking` left it.
 */
public class AnthropicLlmProvider internal constructor(
    private val o11y: Observer,
    private val httpClient: HttpClient,
    private val circuitBreaker: CircuitBreaker,
    private val apiKey: String,
    private val messagesUrl: String,
    private val defaultModel: String,
    private val maxTokens: Int,
) : LlmProvider {
    override suspend fun complete(params: CompletionParams): CompletionResult =
        o11y.span("Completion") {
            val model = params.model.orEmpty().ifBlank { defaultModel }.ifBlank { AnthropicModels.DEFAULT }
            set(LlmKeys.MODEL, model)
            set(LlmKeys.MESSAGE_COUNT, params.messages.size)

            // Anthropic's Messages API only accepts `user`/`assistant` in messages[]. `Role.SYSTEM`
            // prompts are hoisted into the top-level `system` field (multiple joined with newlines,
            // Anthropic's single-string system form), and `Role.TOOL` is rejected: the shared Message
            // model carries only flat content with no tool_use_id to build a `tool_result` block, so a
            // fast, explicit failure beats silently sending an invalid role and taking a 400. This runs
            // before the breaker so a malformed request isn't counted as a breaker failure.
            val systemPrompt =
                params.messages
                    .filter { it.role == Role.SYSTEM }
                    .joinToString("\n") { it.content }
                    .ifEmpty { null }

            val apiMessages =
                params.messages.mapNotNull { message ->
                    when (message.role) {
                        Role.SYSTEM -> null // hoisted into `system`
                        Role.TOOL -> throw UnsupportedRoleException(message.role)
                        Role.USER, Role.ASSISTANT -> ApiMessage(message.role.value, message.content)
                    }
                }

            circuitBreaker.execute {
                val payload =
                    MessagesRequest(
                        model = model,
                        maxTokens = maxTokens,
                        messages = apiMessages,
                        system = systemPrompt,
                    )

                val request =
                    HttpRequest.build {
                        method = HttpMethod.POST
                        url = messagesUrl
                        header("x-api-key", apiKey)
                        header("anthropic-version", ANTHROPIC_VERSION)
                        header("content-type", "application/json")
                        body(json.encodeToString(MessagesRequest.serializer(), payload))
                    }

                val response = httpClient.execute(request)
                if (!response.isSuccessful) throw AnthropicApiException(response.statusCode)

                val parsed = json.decodeFromString(MessagesResponse.serializer(), response.bodyAsText())
                val usage = parsed.usage?.let { TokenUsage(inputTokens = it.inputTokens, outputTokens = it.outputTokens) }
                usage?.let { set(LlmKeys.TOTAL_TOKENS, it.totalTokens) }
                parsed.stopReason?.let { set(LlmKeys.FINISH_REASON, it) }

                CompletionResult(
                    content = parsed.content.filter { it.type == "text" }.joinToString("") { it.text },
                    usage = usage,
                    finishReason = parsed.stopReason,
                )
            }
        }
}

/**
 * Builds a production Anthropic-backed [LlmProvider].
 *
 * @param apiKey the Anthropic API key; an empty key throws [EmptyApiKeyException] (mirroring Go's
 *   `Required` rule). Sent only as the `x-api-key` header; never recorded.
 * @param httpClient the transport every completion goes over (`:httpclient-okhttp` / `:httpclient-ktor`
 *   in production; a `FakeHttpClient` in tests).
 * @param baseUrl the API base; requests target `<baseUrl>/v1/messages`. Defaults to Anthropic's host.
 * @param defaultModel the model used when a request leaves `model` blank. Defaults to the most capable
 *   Claude ([AnthropicModels.DEFAULT]).
 * @param maxTokens the `max_tokens` sent on every request. Defaults to [AnthropicConfig.DEFAULT_MAX_TOKENS].
 * @param logger optional root logger; defaults to noop.
 * @param tracerProvider optional tracer provider; defaults to noop.
 * @param circuitBreaker optional breaker; defaults to the always-closed noop breaker.
 */
public fun AnthropicLlmProvider(
    apiKey: String,
    httpClient: HttpClient,
    baseUrl: String = AnthropicConfig.DEFAULT_BASE_URL,
    defaultModel: String = AnthropicModels.DEFAULT,
    maxTokens: Int = AnthropicConfig.DEFAULT_MAX_TOKENS,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
): AnthropicLlmProvider {
    if (apiKey.isEmpty()) throw EmptyApiKeyException()
    return AnthropicLlmProvider(
        o11y = Observer(NAME, logger, tracerProvider),
        httpClient = httpClient,
        circuitBreaker = circuitBreaker,
        apiKey = apiKey,
        messagesUrl = baseUrl.trimEnd('/') + "/v1/messages",
        defaultModel = defaultModel,
        maxTokens = maxTokens,
    )
}

/** Builds a production Anthropic-backed [LlmProvider] from an [AnthropicConfig]. Validates the key first. */
public fun AnthropicLlmProvider(
    config: AnthropicConfig,
    httpClient: HttpClient,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
    circuitBreaker: CircuitBreaker = NoopCircuitBreaker,
): AnthropicLlmProvider {
    config.validate()
    return AnthropicLlmProvider(
        apiKey = config.apiKey,
        httpClient = httpClient,
        baseUrl = config.baseUrl,
        defaultModel = config.defaultModel,
        maxTokens = config.maxTokens,
        logger = logger,
        tracerProvider = tracerProvider,
        circuitBreaker = circuitBreaker,
    )
}
