package com.primandproper.platform.search.text.elasticsearch

/*
 * Search request bodies, ported from platform-go's `search/text/elasticsearch/queries.go`. Go builds
 * these by json-marshaling small structs; the shapes are fixed and tiny, so this port emits the same
 * JSON directly with a minimal string escaper rather than pulling a serializer into the hot path.
 */

/**
 * Builds the `multi_match` search body platform-go's `search` method sends: a `best_fields` match of
 * [query] across all fields (`"*"`). The rendered JSON is byte-for-byte the marshaling of Go's
 * `searchQuery{ multiMatchQuery{ Query: query, Type: "best_fields", Fields: ["*"] } }`.
 */
internal fun multiMatchQuery(query: String): String =
    """{"query":{"multi_match":{"query":"${escapeJsonString(query)}","type":"best_fields","fields":["*"]}}}"""

/** The match-all body used by `wipe`'s delete-by-query, matching Go's `{"query":{"match_all":{}}}`. */
internal const val MATCH_ALL_QUERY: String = """{"query":{"match_all":{}}}"""

/** Escapes [value] for embedding inside a JSON string literal (quotes, backslashes, control chars). */
internal fun escapeJsonString(value: String): String {
    val sb = StringBuilder(value.length + 2)
    for (c in value) {
        when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
