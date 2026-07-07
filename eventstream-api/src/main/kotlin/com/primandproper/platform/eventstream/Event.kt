package com.primandproper.platform.eventstream

/**
 * A typed event with a raw JSON payload. Port of platform-go's `eventstream.Event`.
 *
 * Go models the payload as `json.RawMessage` (a `[]byte` holding already-encoded JSON) so the
 * streaming layer forwards it verbatim without re-marshalling. The faithful Kotlin analog is a raw
 * JSON [String]: [payload] is expected to already be valid JSON (an object, array, string, number,
 * …), and both transports forward it as-is — the SSE writer splits it across `data:` lines and the
 * WebSocket writer nests it under the envelope's `"payload"` field.
 *
 * @param type the event name; emitted as the SSE `event:` field and the envelope's `"type"`. Empty
 *   means "no type", matching Go's `omitempty`-free `Type string` where `""` suppresses the field.
 * @param payload the raw JSON payload, or `null` to omit it (the analog of Go's
 *   `json:"payload,omitempty"` dropping a nil `RawMessage`).
 */
public data class Event(
    val type: String = "",
    val payload: String? = null,
)
