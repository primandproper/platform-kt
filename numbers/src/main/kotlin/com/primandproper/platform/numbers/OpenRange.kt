package com.primandproper.platform.numbers

/**
 * A range where both [min] and [max] are optional. Ports platform-go's `OpenRange[T]`.
 *
 * platform-go carries no validation on this type (it is a plain JSON-shaped struct), so none is
 * added here. platform-go's `OpenRangeUpdateRequestInput[T]` is structurally identical to this type
 * and is intentionally not ported as a separate class — reuse [OpenRange] for that update shape.
 */
public data class OpenRange<T : Comparable<T>>(
    public val min: T? = null,
    public val max: T? = null,
)
