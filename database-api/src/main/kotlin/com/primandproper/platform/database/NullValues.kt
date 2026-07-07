package com.primandproper.platform.database

import java.time.Instant

/**
 * Converters between nullable ("null") database values and plain Kotlin values. Port of platform-go's
 * `database/null_values.go`.
 *
 * platform-go models a nullable column with `sql.NullString{String, Valid}`, `sql.NullTime`,
 * `sql.NullInt32`, and friends, and provides a converter per (Go type, null type) pair. The JVM has
 * no such wrapper struct: the idiomatic "no value" is simply a Kotlin nullable (`String?`, `Instant?`,
 * …), where `null` is the analog of `Valid == false`. That collapses the whole family of pure
 * pointer/uint-widening helpers (`StringPointerFromNullString`, `NullInt32FromUint16Pointer`, …) into
 * plain nullability, so only the converters that carry real behavior survive here:
 *
 *  - the "give me a non-null default" readers (`TimeFromNullTime`, `StringFromNullString`,
 *    `BoolFromNullBool`), and
 *  - the float↔string codec platform-go uses to move `NUMERIC` columns through a `sql.NullString`
 *    (Postgres returns them as text), which is the genuinely load-bearing logic in the Go file.
 */
public object NullValues {
    /** Returns [value], or [Instant.EPOCH] when it is `null`. Port of `TimeFromNullTime` (Go returns the zero time). */
    public fun timeOrEpoch(value: Instant?): Instant = value ?: Instant.EPOCH

    /** Returns [value], or the empty string when it is `null`. Port of `StringFromNullString`. */
    public fun stringOrEmpty(value: String?): String = value ?: ""

    /** Returns [value], or `false` when it is `null`. Port of `BoolFromNullBool`. */
    public fun boolOrFalse(value: Boolean?): Boolean = value ?: false

    /** Parses [value] as a float, returning `0f` when it does not parse. Port of `Float32FromString`. */
    public fun floatFromString(value: String): Float = value.toDoubleOrNull()?.toFloat() ?: 0f

    /**
     * Parses a nullable NUMERIC-as-text column into a nullable float, returning `null` when the column
     * is `null` or does not parse. Port of `Float32PointerFromNullString`.
     */
    public fun floatFromNullableString(value: String?): Float? = value?.toDoubleOrNull()?.toFloat()

    /**
     * Parses a nullable NUMERIC-as-text column into a nullable double, returning `null` when the column
     * is `null` or does not parse. Port of `Float64PointerFromNullString`.
     */
    public fun doubleFromNullableString(value: String?): Double? = value?.toDoubleOrNull()

    /** Parses a nullable NUMERIC-as-text column into a float, returning `0f` on `null`/unparseable. Port of `Float32FromNullString`. */
    public fun floatOrZeroFromNullableString(value: String?): Float = floatFromNullableString(value) ?: 0f

    /** Formats [value] as its minimal decimal string, for storing a float in a text column. Port of `StringFromFloat32`. */
    public fun stringFromFloat(value: Float): String = value.toString()

    /** Formats [value] as its minimal decimal string, for storing a double in a text column. Port of `StringFromFloat64`. */
    public fun stringFromDouble(value: Double): String = value.toString()

    /** Formats a nullable float for a nullable text column: `null` stays `null`. Port of `NullStringFromFloat32Pointer`. */
    public fun stringFromNullableFloat(value: Float?): String? = value?.let { stringFromFloat(it) }

    /** Formats a nullable double for a nullable text column: `null` stays `null`. Port of `NullStringFromFloat64Pointer`. */
    public fun stringFromNullableDouble(value: Double?): String? = value?.let { stringFromDouble(it) }
}
