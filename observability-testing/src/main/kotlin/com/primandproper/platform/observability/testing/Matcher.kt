package com.primandproper.platform.observability.testing

/**
 * Matches a recorded [Observation], optionally constrained to a pillar via [onSpan] / [onLog].
 * Port of platform-go's recording `Matcher`.
 */
public class Matcher internal constructor(
    internal val desc: String,
    private val pillarFilter: Pillar?,
    private val predicate: (Observation) -> Boolean,
) {
    /** Requires the observation to have reached the span (Set or SpanOnly). */
    public fun onSpan(): Matcher = Matcher("$desc on span", Pillar.SPAN, predicate)

    /** Requires the observation to have reached the logger (Set or LogOnly). */
    public fun onLog(): Matcher = Matcher("$desc on log", Pillar.LOG, predicate)

    internal fun matches(o: Observation): Boolean {
        if (pillarFilter != null && !reached(o.pillar, pillarFilter)) return false
        return predicate(o)
    }

    private fun reached(
        actual: Pillar,
        wanted: Pillar,
    ): Boolean =
        when (wanted) {
            Pillar.SPAN -> actual == Pillar.BOTH || actual == Pillar.SPAN
            Pillar.LOG -> actual == Pillar.BOTH || actual == Pillar.LOG
            Pillar.BOTH -> actual == Pillar.BOTH
        }
}

/** Matches any observation of [key], regardless of value. */
public fun observedKey(key: String): Matcher = Matcher("key=$key", null) { it.key == key }

/** Matches an observation of [key] with exactly [value]. */
public fun observedKeyValue(
    key: String,
    value: Any?,
): Matcher = Matcher("key=$key value=$value", null) { it.key == key && it.value == value }

/** Matches any observation whose value equals [value], regardless of key. */
public fun observedValue(value: Any?): Matcher = Matcher("value=$value", null) { it.value == value }
