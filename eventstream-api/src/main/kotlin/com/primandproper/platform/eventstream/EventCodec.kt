package com.primandproper.platform.eventstream

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Encodes/decodes an [Event] as the JSON envelope the WebSocket transport puts on the wire. Port of
 * the `conn.WriteJSON(event)` / `json.Unmarshal(msg, &event)` pair platform-go's websocket stream
 * uses — the same `{"type":"...","payload":<raw>}` shape.
 *
 * Only the runtime `JsonElement` API of kotlinx-serialization is used (no `@Serializable`, no
 * compiler plugin): [Event.payload] is already raw JSON, so [encode] nests it under `"payload"`
 * verbatim and [decode] hands the sub-tree back as its raw JSON text — the direct analog of Go's
 * `json.RawMessage` passing through untouched.
 *
 * SSE does not use this: it frames [Event.type] and [Event.payload] into `event:` / `data:` lines
 * directly (see the SSE transport), matching Go's `sse.Send`.
 */
public object EventCodec {
    /**
     * Renders [event] as its JSON envelope. A `null` [Event.payload] is omitted, mirroring Go's
     * `json:"payload,omitempty"`.
     */
    public fun encode(event: Event): String =
        buildJsonObject {
            put("type", event.type)
            event.payload?.let { put("payload", Json.parseToJsonElement(it)) }
        }.toString()

    /**
     * Parses a JSON envelope back into an [Event]. A missing/blank `"type"` becomes `""` and a
     * missing `"payload"` becomes `null`; the payload sub-tree is returned as its raw JSON text so it
     * round-trips byte-for-byte through [encode], the way Go's `json.RawMessage` does.
     *
     * Throws if [text] is not a JSON object — callers on the receive path swallow that to skip a
     * malformed frame, exactly as Go's `readLoop` `continue`s past an `Unmarshal` error.
     */
    public fun decode(text: String): Event {
        val obj = Json.parseToJsonElement(text).jsonObject
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: ""
        val payload = obj["payload"]?.toString()
        return Event(type, payload)
    }
}

/**
 * Convenience: decode each element of a raw-JSON [Flow] into an [Event], dropping any element that
 * fails to parse — the [Flow] analog of the WebSocket read loop skipping malformed frames.
 */
public fun Flow<String>.decodeEvents(): Flow<Event> {
    val source = this
    return kotlinx.coroutines.flow.flow {
        source.collect { text ->
            val event = runCatching { EventCodec.decode(text) }.getOrNull()
            if (event != null) emit(event)
        }
    }
}
