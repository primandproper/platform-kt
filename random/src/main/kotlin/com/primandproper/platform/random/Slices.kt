package com.primandproper.platform.random

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * A [Random] backed by [SecureRandom], for call sites that want [List.shuffled] / [List.random] /
 * [randomElementOrNull] to draw from a CSPRNG rather than the stdlib's default (non-secure,
 * `xorshift`-derived) source. Lazily constructed since building a [SecureRandom] can block briefly
 * while the platform seeds its entropy pool.
 */
public val SecureKotlinRandom: Random by lazy { SecureRandom().asKotlinRandom() }

/**
 * Fetches a random element from the receiver, or `null` if it is empty.
 *
 * Port of platform-go's `random.Element[T any](s []T) T`, which deliberately uses `math/rand/v2`
 * rather than `crypto/rand` (selecting an already-in-memory element doesn't need cryptographic
 * strength) and returns the zero value of `T` for an empty slice rather than panicking. Kotlin has no
 * generic zero value, so `null` is the idiomatic substitute — the Go test's
 * `Element([]string{}) == ""` / `Element([]int(nil)) == 0` become
 * `emptyList<String>().randomElementOrNull() ?: ""` / `?: 0` at the call site if a default is truly
 * needed.
 *
 * Uses [Random.Default] (not [SecureKotlinRandom]) by default, matching Go's non-crypto choice; pass
 * [SecureKotlinRandom] explicitly where the selection itself must be unpredictable.
 */
public fun <T> List<T>.randomElementOrNull(random: Random = Random.Default): T? = if (isEmpty()) null else this[random.nextInt(size)]

/**
 * Returns a shuffled copy of the receiver using a cryptographically secure random source. Not present
 * in the Go source (`slices.go` only has `Element`) — Kotlin's stdlib already provides
 * `shuffled(random: Random)`, so this is just a convenience wrapper supplying [SecureKotlinRandom] for
 * callers who want the stronger source without wiring it up themselves.
 */
public fun <T> List<T>.secureShuffled(): List<T> = shuffled(SecureKotlinRandom)
