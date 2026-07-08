package com.primandproper.platform.bitmask

/*
Port of platform-go's `bitmask` package: an immutable bitmask with set-algebra operations. Every
operation returns a new Bitmask, leaving the receiver untouched.

Overlap with the standard library: the JDK already offers `java.util.EnumSet` (a fast set over enum
constants) and raw `Long` bit twiddling covers most of what this does. This port is preferred where
faithfulness to platform-go matters — it keeps the exact `Set`/`Clear`/`Has`/`Union`/… surface, the
width-aware zero-padded `toString`, and value semantics — so code reads the same across the Go and
Kotlin sides. Reach for `EnumSet` instead when you want an idiomatic Kotlin/Java set and don't need
the shared API or the packed integer representation (e.g. persistence, wire formats).

Divergence from Go: `Bitmask[T Unsigned]` is generic over the four fixed-width unsigned types. Kotlin
cannot constrain a type parameter to its unsigned types and run bit operations on it generically, so
the width the type parameter carried in Go — used for `toString` padding and range-checked parsing —
becomes an explicit `width` (8, 16, 32, or 64 bits) over a single `ULong` backing value, which is
masked to that width on construction. Go's `MarshalJSON`/`UnmarshalJSON` (bare-number encoding) are
ported as [toJsonNumber] / [Companion.parseJsonNumber] rather than a serialization-framework hook, so
the module stays stdlib-only; the semantics — decimal number in, width range-checked on the way back
— are preserved.
*/
public class Bitmask private constructor(
    /** The underlying integer value, masked to [width] bits. */
    public val value: ULong,
    /** The bit width backing this mask: one of 8, 16, 32, or 64. */
    public val width: Int,
) {
    /** Returns a new Bitmask with the given [flags] set (bitwise OR). */
    public fun set(vararg flags: ULong): Bitmask {
        var v = value
        for (f in flags) v = v or f
        return of(width, v)
    }

    /** Returns a new Bitmask with the given [flags] cleared (bitwise AND-NOT). */
    public fun clear(vararg flags: ULong): Bitmask {
        var v = value
        for (f in flags) v = v and f.inv()
        return of(width, v)
    }

    /** Returns a new Bitmask with the given [flags] toggled (bitwise XOR). */
    public fun toggle(vararg flags: ULong): Bitmask {
        var v = value
        for (f in flags) v = v xor f
        return of(width, v)
    }

    /** Reports whether [flag] is set. A zero flag is never considered set, matching Go. */
    public fun has(flag: ULong): Boolean = flag != 0uL && value and flag == flag

    /** Reports whether every one of [flags] is set. Empty input (combined zero) reports false. */
    public fun hasAll(vararg flags: ULong): Boolean {
        var combined = 0uL
        for (f in flags) combined = combined or f
        return combined != 0uL && value and combined == combined
    }

    /** Reports whether any of [flags] is set. Empty input reports false. */
    public fun hasAny(vararg flags: ULong): Boolean {
        var combined = 0uL
        for (f in flags) combined = combined or f
        return value and combined != 0uL
    }

    /** Reports whether no flags are set. */
    public fun isEmpty(): Boolean = value == 0uL

    /** Returns the number of set bits (population count). */
    public fun count(): Int = value.countOneBits()

    /** Returns a new Bitmask with the flags set in either mask. Widths must match. */
    public fun union(other: Bitmask): Bitmask {
        require(width == other.width) { "cannot combine bitmasks of different widths: $width != ${other.width}" }
        return of(width, value or other.value)
    }

    /** Returns a new Bitmask with only the flags set in both masks. Widths must match. */
    public fun intersect(other: Bitmask): Bitmask {
        require(width == other.width) { "cannot combine bitmasks of different widths: $width != ${other.width}" }
        return of(width, value and other.value)
    }

    /** Returns a new Bitmask with the flags set in this mask but not in [other]. Widths must match. */
    public fun difference(other: Bitmask): Bitmask {
        require(width == other.width) { "cannot combine bitmasks of different widths: $width != ${other.width}" }
        return of(width, value and other.value.inv())
    }

    /** Returns a [width]-digit, zero-padded binary string, mirroring Go's `%0*b` Stringer. */
    override fun toString(): String = value.toString(radix = 2).padStart(width, '0')

    /** Encodes the mask as a bare decimal number, the port of Go's `MarshalJSON`. */
    public fun toJsonNumber(): String = value.toString()

    /** Two bitmasks are equal when both their value and width match. */
    override fun equals(other: Any?): Boolean = this === other || (other is Bitmask && value == other.value && width == other.width)

    override fun hashCode(): Int = 31 * value.hashCode() + width

    public companion object {
        /**
         * Creates a Bitmask of [width] bits (8, 16, 32, or 64) with the given [flags] set. Values are
         * OR-folded and masked to the width, mirroring Go's `New[T](flags ...T)`.
         */
        public fun of(
            width: Int,
            vararg flags: ULong,
        ): Bitmask {
            var v = 0uL
            for (f in flags) v = v or f
            return of(width, v)
        }

        /**
         * Creates a Bitmask of [width] bits from a raw [value], masked to the width. The two-argument
         * port of Go's `FromValue[T](value T)`; the single-value internal factory used by every
         * operation to re-mask its result.
         */
        public fun of(
            width: Int,
            value: ULong,
        ): Bitmask {
            require(width in WIDTHS) { "unsupported bitmask width: $width (must be one of $WIDTHS)" }
            return Bitmask(value and mask(width), width)
        }

        /**
         * Decodes a bare decimal number into a [width]-bit Bitmask, the port of Go's `UnmarshalJSON`.
         * Throws [IllegalArgumentException] if [text] is not a non-negative integer or does not fit
         * the width — a value exceeding the width errors rather than truncating, as in Go.
         */
        public fun parseJsonNumber(
            width: Int,
            text: String,
        ): Bitmask {
            require(width in WIDTHS) { "unsupported bitmask width: $width (must be one of $WIDTHS)" }
            val parsed =
                text.trim().toULongOrNull()
                    ?: throw IllegalArgumentException("bitmask: invalid JSON value: $text")
            require(parsed <= mask(width)) { "bitmask: value $parsed exceeds $width-bit width" }
            return Bitmask(parsed, width)
        }

        private val WIDTHS = setOf(8, 16, 32, 64)

        /** The all-ones mask for [width] bits. */
        private fun mask(width: Int): ULong = if (width >= 64) ULong.MAX_VALUE else (1uL shl width) - 1uL
    }
}
