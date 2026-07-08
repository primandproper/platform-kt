package com.primandproper.platform.authentication.tokens.jwt

/*
 * A minimal, dependency-free JSON codec for JWT claim sets. The JWT wire format is
 * base64url(header) "." base64url(payload) "." base64url(signature), where header and payload are
 * JSON objects — so a faithful HS256 signer needs exactly enough JSON to serialize a claim map and
 * parse it back. The JDK ships no JSON, and the module deliberately avoids a JSON vendor (tokens are
 * pure javax.crypto), so this hand-rolls the small subset that claim values ever occupy: strings,
 * whole numbers (Long), fractional numbers (Double), booleans, null, nested objects, and arrays.
 */

/** Serializes a claim map to a compact JSON object string. */
internal fun encodeJsonObject(map: Map<String, Any?>): String {
    val sb = StringBuilder()
    sb.append('{')
    var first = true
    for ((k, v) in map) {
        if (!first) sb.append(',')
        first = false
        encodeString(sb, k)
        sb.append(':')
        encodeValue(sb, v)
    }
    sb.append('}')
    return sb.toString()
}

private fun encodeValue(
    sb: StringBuilder,
    value: Any?,
) {
    when (value) {
        null -> sb.append("null")
        is String -> encodeString(sb, value)
        is Boolean -> sb.append(value.toString())
        is Int -> sb.append(value.toString())
        is Long -> sb.append(value.toString())
        is Short -> sb.append(value.toString())
        is Byte -> sb.append(value.toString())
        is Double -> sb.append(value.toString())
        is Float -> sb.append(value.toString())
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            sb.append(encodeJsonObject(value as Map<String, Any?>))
        }
        is List<*> -> {
            sb.append('[')
            for ((i, e) in value.withIndex()) {
                if (i > 0) sb.append(',')
                encodeValue(sb, e)
            }
            sb.append(']')
        }
        else -> encodeString(sb, value.toString())
    }
}

private fun encodeString(
    sb: StringBuilder,
    s: String,
) {
    sb.append('"')
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else ->
                if (c < ' ') {
                    sb.append("\\u")
                    sb.append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
        }
    }
    sb.append('"')
}

/** Parses a JSON object string into a map. Throws [IllegalArgumentException] on malformed input. */
internal fun decodeJsonObject(text: String): Map<String, Any?> {
    val parser = JsonParser(text)
    parser.skipWhitespace()
    val value = parser.parseValue()
    parser.skipWhitespace()
    require(parser.atEnd()) { "trailing content after JSON value" }
    @Suppress("UNCHECKED_CAST")
    return value as? Map<String, Any?> ?: throw IllegalArgumentException("expected a JSON object")
}

private class JsonParser(
    private val text: String,
) {
    private var pos = 0

    fun atEnd(): Boolean = pos >= text.length

    fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    fun parseValue(): Any? {
        skipWhitespace()
        require(pos < text.length) { "unexpected end of JSON" }
        return when (text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            else -> parseNumber()
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return map
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return map
                }
                else -> throw IllegalArgumentException("expected ',' or '}' in object")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return list
        }
        while (true) {
            list.add(parseValue())
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return list
                }
                else -> throw IllegalArgumentException("expected ',' or ']' in array")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            require(pos < text.length) { "unterminated string" }
            val c = text[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    require(pos < text.length) { "unterminated escape" }
                    when (val e = text[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            // Fail with the parser's own IllegalArgumentException on a truncated or
                            // non-hex escape, not a StringIndexOutOfBounds/NumberFormatException — this
                            // parses the attacker-controlled JWT header before signature verification.
                            require(pos + 4 <= text.length) { "truncated \\u escape" }
                            val hex = text.substring(pos, pos + 4)
                            pos += 4
                            val code = hex.toIntOrNull(16) ?: throw IllegalArgumentException("invalid \\u escape: \\u$hex")
                            sb.append(code.toChar())
                        }
                        else -> throw IllegalArgumentException("invalid escape: \\$e")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun parseBoolean(): Boolean =
        when {
            text.startsWith("true", pos) -> {
                pos += 4
                true
            }
            text.startsWith("false", pos) -> {
                pos += 5
                false
            }
            else -> throw IllegalArgumentException("invalid literal")
        }

    private fun parseNull(): Any? {
        require(text.startsWith("null", pos)) { "invalid literal" }
        pos += 4
        return null
    }

    private fun parseNumber(): Any {
        val start = pos
        if (peek() == '-') pos++
        var isDouble = false
        while (pos < text.length) {
            val c = text[pos]
            when {
                c in '0'..'9' -> pos++
                c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-' -> {
                    isDouble = true
                    pos++
                }
                else -> break
            }
        }
        val token = text.substring(start, pos)
        require(token.isNotEmpty() && token != "-") { "invalid number" }
        return if (isDouble) token.toDouble() else (token.toLongOrNull() ?: token.toDouble())
    }

    private fun peek(): Char {
        require(pos < text.length) { "unexpected end of JSON" }
        return text[pos]
    }

    private fun expect(c: Char) {
        require(pos < text.length && text[pos] == c) { "expected '$c'" }
        pos++
    }
}
