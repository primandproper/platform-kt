package com.primandproper.platform.eventstream

import com.primandproper.platform.errors.newErrorf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The supported event-stream transports. Port of platform-go's `config.ProviderSSE` /
 * `config.ProviderWebSocket` constants. [value] is the wire/string form validated against
 * configuration.
 */
public enum class EventStreamProvider(
    public val value: String,
) {
    SSE("sse"),
    WEBSOCKET("websocket"),
    ;

    /** Whether this transport can carry client-to-server events. SSE is one-way; WebSocket is not. */
    public val supportsBidirectional: Boolean get() = this == WEBSOCKET

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or `null` if it
         * names no known provider — mirroring Go's `validation.In(ProviderSSE, ProviderWebSocket)`
         * and the `strings.TrimSpace(strings.ToLower(...))` switch in `ProvideEventStreamUpgrader`.
         */
        public fun fromValue(value: String): EventStreamProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * WebSocket-specific configuration. Port of platform-go's `eventstream/websocket.Config`.
 *
 * @param allowedOrigins exact `Origin` header values permitted to upgrade. When empty, the transport
 *   falls back to the framework's same-origin policy, matching Go's `originChecker` returning `nil`
 *   (gorilla's default) for an empty allowlist.
 * @param heartbeatInterval how often to ping an idle connection; `Duration.ZERO` disables the
 *   heartbeat. Defaults to 30s, matching Go's `defaultHeartbeatInterval`.
 * @param readBufferSize / [writeBufferSize] frame buffer sizes; `0` means "use the transport
 *   default", matching Go's `defaultBufferSize` (1024) fallback.
 */
public data class WebSocketConfig(
    val allowedOrigins: List<String> = emptyList(),
    val heartbeatInterval: Duration = DEFAULT_HEARTBEAT_INTERVAL,
    val readBufferSize: Int = 0,
    val writeBufferSize: Int = 0,
) {
    /**
     * Whether a request bearing [origin] may upgrade. Port of Go's `originChecker`: an empty
     * [allowedOrigins] permits everything (the framework's same-origin default applies), an empty or
     * absent `Origin` header is treated as an originless non-browser client and allowed, and
     * otherwise the exact value must be in the allowlist.
     */
    public fun isOriginAllowed(origin: String?): Boolean {
        if (allowedOrigins.isEmpty()) return true
        if (origin.isNullOrEmpty()) return true
        return origin in allowedOrigins
    }

    public companion object {
        /** Go's `defaultHeartbeatInterval`. */
        public val DEFAULT_HEARTBEAT_INTERVAL: Duration = 30.seconds

        /** Go's `defaultBufferSize`. */
        public const val DEFAULT_BUFFER_SIZE: Int = 1024
    }
}

/**
 * Provider-agnostic event-stream configuration. Port of platform-go's `eventstream/config.Config`:
 * the chosen [provider] plus the optional [webSocket] settings.
 *
 * Go validates that `WebSocket` is present when `Provider == websocket` (`validation.When(...,
 * Required)`); [resolveProvider] performs the equivalent check, throwing when the WebSocket transport
 * is selected without its settings. The actual endpoint construction is transport- and
 * framework-specific and therefore lives in `:eventstream-ktor` (server emit), not here — the Kotlin
 * analog of Go's `ProvideEventStreamUpgrader` switch, but split out because the Ktor upgrade is done
 * through route builders rather than a servlet-style `http.ResponseWriter` upgrader.
 */
public data class EventStreamConfig(
    val provider: EventStreamProvider,
    val webSocket: WebSocketConfig? = null,
) {
    /**
     * Validates the configuration and returns the resolved [provider]. Mirrors Go's
     * `ValidateWithContext` + the `ProvideEventStreamUpgrader` switch: an unknown provider or a
     * WebSocket provider missing its [webSocket] settings is an error.
     */
    public fun resolveProvider(): EventStreamProvider {
        if (provider == EventStreamProvider.WEBSOCKET && webSocket == null) {
            throw newErrorf("websocket provider requires websocket configuration")
        }
        return provider
    }
}
