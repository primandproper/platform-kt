package com.primandproper.platform.httpclient

/**
 * An immutable, case-insensitive multimap of HTTP headers. HTTP header names compare
 * case-insensitively (and HTTP/2 requires them lowercased), so keys are normalised to lowercase on
 * the way in — which is also what makes [HeaderRedaction] able to match `Authorization` regardless
 * of the casing a caller used.
 *
 * A single name can carry multiple values (`Set-Cookie`, `Vary`, …), so values are lists.
 */
public class HttpHeaders private constructor(
    private val byName: Map<String, List<String>>,
) {
    /** All values for [name] (case-insensitive), or an empty list if absent. */
    public operator fun get(name: String): List<String> = byName[name.lowercase()] ?: emptyList()

    /** The first value for [name], or null. */
    public fun first(name: String): String? = get(name).firstOrNull()

    public fun contains(name: String): Boolean = byName.containsKey(name.lowercase())

    /** The (lowercased) header names present. */
    public fun names(): Set<String> = byName.keys

    public fun isEmpty(): Boolean = byName.isEmpty()

    /** Iterates each header name with all of its values. */
    public inline fun forEach(action: (name: String, values: List<String>) -> Unit) {
        asMap().forEach { (name, values) -> action(name, values) }
    }

    /** The backing map (names lowercased). Never mutated. */
    public fun asMap(): Map<String, List<String>> = byName

    public class Builder {
        private val entries = LinkedHashMap<String, MutableList<String>>()

        /** Appends a value, preserving any already recorded under [name]. */
        public fun add(
            name: String,
            value: String,
        ): Builder {
            entries.getOrPut(name.lowercase()) { mutableListOf() }.add(value)
            return this
        }

        /** Replaces every value under [name] with [value]. */
        public fun set(
            name: String,
            value: String,
        ): Builder {
            entries[name.lowercase()] = mutableListOf(value)
            return this
        }

        public fun build(): HttpHeaders = HttpHeaders(entries.mapValues { (_, values) -> values.toList() })
    }

    public companion object {
        public val EMPTY: HttpHeaders = HttpHeaders(emptyMap())

        public fun of(vararg pairs: Pair<String, String>): HttpHeaders {
            val builder = Builder()
            for ((name, value) in pairs) builder.add(name, value)
            return builder.build()
        }

        public inline fun build(block: Builder.() -> Unit): HttpHeaders = Builder().apply(block).build()
    }
}
