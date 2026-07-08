package com.primandproper.platform.numbers

/**
 * A range with a required [min] and an optional [max]. Ports platform-go's `MinRange[T]`.
 *
 * platform-go validates through ozzo-validation with a context; here [validate] is a plain check.
 * As in Go, [min] is a value that is always present, so a range starting at the type's zero (`0`,
 * `0.0f`, …) is legitimate and never rejected — only a [max] that falls below [min] is invalid.
 */
public data class MinRange<T : Comparable<T>>(
    public val min: T,
    public val max: T? = null,
) {
    /**
     * Verifies that [max], when set, is not below [min]. Throws [IllegalArgumentException] otherwise,
     * standing in for the `error` platform-go's `ValidateWithContext` returns.
     */
    public fun validate() {
        val upper = max
        require(upper == null || upper >= min) { "max must be greater than or equal to min" }
    }
}
