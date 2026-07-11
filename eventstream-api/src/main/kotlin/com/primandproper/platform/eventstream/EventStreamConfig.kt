package com.primandproper.platform.eventstream

import com.primandproper.platform.errors.newError
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
 *   enforces gorilla's default same-origin policy (see [isOriginAllowed]) — a cross-origin request is
 *   rejected. This is a deliberate secure default: unlike Go, Ktor's WebSockets plugin performs no
 *   Origin validation of its own, so an empty allowlist must not mean "allow everything".
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
     * Whether a request whose `Origin` header is [origin] and whose `Host` header is [host] may
     * upgrade. Port of Go's gorilla-backed `originChecker`:
     *
     * - An absent or blank `Origin` is an originless (non-browser) client and is allowed, matching
     *   gorilla's `checkSameOrigin` returning `true` for a missing `Origin`. Browsers always send an
     *   `Origin` on cross-site requests, so this cannot be spoofed away by a malicious page.
     * - With a non-empty [allowedOrigins], the exact `Origin` value must appear in the allowlist.
     * - With an empty [allowedOrigins], gorilla's default same-origin policy applies: the `Origin`
     *   authority (`scheme://host[:port]`) must equal the request [host] (`host[:port]`), compared
     *   case-insensitively. A cross-origin or unparseable `Origin` is rejected. This replaces the
     *   previous "empty allowlist allows everything" behavior, which — because Ktor's WebSockets
     *   plugin performs no Origin validation of its own — left endpoints open to Cross-Site WebSocket
     *   Hijacking.
     */
    public fun isOriginAllowed(
        origin: String?,
        host: String?,
    ): Boolean {
        if (origin.isNullOrBlank()) return true
        if (allowedOrigins.isNotEmpty()) return origin in allowedOrigins
        val originHost = originAuthority(origin) ?: return false
        return !host.isNullOrBlank() && originHost.equals(host, ignoreCase = true)
    }

    public companion object {
        /** Go's `defaultHeartbeatInterval`. */
        public val DEFAULT_HEARTBEAT_INTERVAL: Duration = 30.seconds

        /** Go's `defaultBufferSize`. */
        public const val DEFAULT_BUFFER_SIZE: Int = 1024
    }
}

/**
 * Extracts the `host[:port]` authority from an `Origin` header value (`scheme://host[:port]`), or
 * `null` when it carries no scheme separator (and so cannot be parsed) — mirroring the `Host` field
 * of gorilla's `url.Parse(origin)`. Comparison against the request `Host` is left to the caller.
 */
private fun originAuthority(origin: String): String? {
    val schemeEnd = origin.indexOf("://")
    if (schemeEnd < 0) return null
    val rest = origin.substring(schemeEnd + 3)
    val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (end < 0) rest else rest.substring(0, end)
    return authority.ifEmpty { null }
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
            throw newError("websocket provider requires websocket configuration")
        }
        return provider
    }
}
