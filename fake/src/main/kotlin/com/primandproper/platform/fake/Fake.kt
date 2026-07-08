/*
Package com.primandproper.platform.fake provides generic test-data generation utilities for creating
fake instances of arbitrary types — the Kotlin port of platform-go's `fake` package.

platform-go's `fake` is a thin wrapper over two reflection-based faker libraries: it fills any struct
by walking its fields and synthesizing a plausible value for each. This module reproduces that surface
with kotlin-reflect, walking a class's primary constructor instead of struct fields. Every value it
emits is drawn from a single seedable random source, so the whole generator is deterministic: two
[Fake] instances built from the same seed produce identical output — the property test code relies on.
*/
package com.primandproper.platform.fake

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.typeOf

/**
 * A reflection-driven generator of fake test data, port of platform-go's `fake` package.
 *
 * Every value — primitives, strings, times, and the fields of reflectively constructed data classes —
 * is drawn from a single [Random], so a [Fake] built with a fixed [seed][Fake] yields reproducible
 * output. Construct with the no-arg constructor for nondeterministic data, or with a seed to make a
 * test deterministic. Instances are cheap; share or discard freely.
 */
public class Fake private constructor(
    private val random: Random,
    private val maxDepth: Int,
) {
    /**
     * Creates a seeded generator. Two `Fake(seed)` instances produce byte-for-byte identical output,
     * which is what makes seeded test data reproducible. [maxDepth] caps how far reflective
     * construction recurses into nested data classes before giving up (mirroring go-faker's
     * recursion-depth option), guarding against self-referential types.
     */
    public constructor(seed: Long, maxDepth: Int = DEFAULT_MAX_DEPTH) : this(Random(seed), maxDepth)

    /** Creates a nondeterministic generator backed by [Random.Default]. */
    public constructor() : this(Random.Default, DEFAULT_MAX_DEPTH)

    /**
     * Builds a fake instant, truncated to whole seconds and always in UTC (instants are inherently
     * UTC). Port of platform-go's `BuildFakeTime`; the returned value is never the epoch/zero instant.
     */
    public fun buildFakeTime(): Instant {
        val offset = random.nextLong(-HUNDRED_YEARS_SECONDS, HUNDRED_YEARS_SECONDS)
        return Instant.ofEpochSecond(REFERENCE_EPOCH_SECONDS + offset).truncatedTo(ChronoUnit.SECONDS)
    }

    /**
     * Builds a fake instance of [X], returning a [Result] that fails rather than throws when the type
     * cannot be faked (e.g. an interface, [Any], or a class without a primary constructor). Port of
     * platform-go's `BuildFake`, whose `(*X, error)` return this [Result] mirrors.
     */
    public inline fun <reified X : Any> buildFake(): Result<X> =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            fakeValue(typeOf<X>(), 0, null) as X
        }

    /**
     * Builds a fake instance of [X], throwing when the type cannot be faked. Port of platform-go's
     * `MustBuildFake`, whose panic-on-error this throw mirrors.
     */
    public inline fun <reified X : Any> mustBuildFake(): X = buildFake<X>().getOrThrow()

    /**
     * Builds a fake instance of [X] for use in a test, throwing (and so failing the test) when the
     * type cannot be faked. Port of platform-go's `BuildFakeForTest`; Kotlin needs no test handle
     * because a thrown exception already fails the surrounding JUnit test.
     */
    public inline fun <reified X : Any> buildFakeForTest(): X = mustBuildFake<X>()

    /**
     * Synthesizes a value for [type], recursing to [depth] and using [hint] (a constructor parameter
     * name, when known) to shape strings. Exposed only so the reified [buildFake] can inline against
     * it; not part of the intended surface.
     */
    @PublishedApi
    internal fun fakeValue(
        type: KType,
        depth: Int,
        hint: String?,
    ): Any? {
        val klass =
            type.classifier as? KClass<*>
                ?: throw IllegalArgumentException("cannot fake type without a class classifier: $type")

        return when (klass) {
            String::class -> fakeString(hint)
            Boolean::class -> random.nextBoolean()
            Int::class -> random.nextInt()
            Long::class -> random.nextLong()
            Short::class -> random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE + 1).toShort()
            Byte::class -> random.nextInt(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE + 1).toByte()
            Double::class -> random.nextDouble()
            Float::class -> random.nextFloat()
            Char::class -> ('a'..'z').random(random)
            Instant::class -> buildFakeTime()
            UUID::class -> UUID(random.nextLong(), random.nextLong())
            List::class, Collection::class, Iterable::class -> fakeCollection(type, depth)
            Set::class -> fakeCollection(type, depth).toSet()
            Map::class -> fakeMap(type, depth)
            else -> fakeComposite(klass, type, depth)
        }
    }

    private fun fakeComposite(
        klass: KClass<*>,
        type: KType,
        depth: Int,
    ): Any? {
        val enumConstants = klass.java.enumConstants
        if (enumConstants != null && enumConstants.isNotEmpty()) {
            return enumConstants[random.nextInt(enumConstants.size)]
        }

        if (depth >= maxDepth) {
            if (type.isMarkedNullable) return null
            throw IllegalArgumentException("recursion limit ($maxDepth) exceeded while faking $type")
        }

        // Any/Object is technically constructible (its primary constructor is non-null and takes no
        // args), but faking it yields a structureless object — reject it, mirroring Go's panic on
        // BuildFake[any].
        val constructor =
            klass.primaryConstructor?.takeIf { klass != Any::class }
                ?: throw IllegalArgumentException("cannot fake type without a usable primary constructor: $type")
        constructor.isAccessible = true
        val args = constructor.parameters.map { param -> fakeValue(param.type, depth + 1, param.name) }
        return constructor.call(*args.toTypedArray())
    }

    private fun fakeCollection(
        type: KType,
        depth: Int,
    ): List<Any?> {
        val element =
            type.arguments.firstOrNull()?.type
                ?: throw IllegalArgumentException("cannot fake a collection with no element type: $type")
        return List(random.nextInt(1, MAX_COLLECTION_SIZE + 1)) { fakeValue(element, depth + 1, null) }
    }

    private fun fakeMap(
        type: KType,
        depth: Int,
    ): Map<Any?, Any?> {
        val keyType =
            type.arguments.getOrNull(0)?.type
                ?: throw IllegalArgumentException("cannot fake a map with no key type: $type")
        val valueType =
            type.arguments.getOrNull(1)?.type
                ?: throw IllegalArgumentException("cannot fake a map with no value type: $type")
        return (0 until random.nextInt(1, MAX_COLLECTION_SIZE + 1)).associate {
            fakeValue(keyType, depth + 1, null) to fakeValue(valueType, depth + 1, null)
        }
    }

    // Shapes a string from the parameter name, echoing go-faker's field-name heuristics: an "email"
    // field gets an email-shaped value, a "name" field a two-part name, and so on. Falls back to a
    // random lowercase word. Everything is drawn from [random], so it stays reproducible under a seed.
    private fun fakeString(hint: String?): String {
        val h = hint?.lowercase() ?: ""
        return when {
            "email" in h -> "${fakeWord()}@${fakeWord()}.com"
            "firstname" in h || "lastname" in h -> fakeWord().replaceFirstChar { it.uppercase() }
            "name" in h -> "${capitalizedWord()} ${capitalizedWord()}"
            "url" in h -> "https://${fakeWord()}.com"
            "phone" in h -> buildString { repeat(PHONE_DIGITS) { append(random.nextInt(0, 10)) } }
            "uuid" in h || "id" in h -> UUID(random.nextLong(), random.nextLong()).toString()
            else -> fakeWord()
        }
    }

    private fun capitalizedWord(): String = fakeWord().replaceFirstChar { it.uppercase() }

    private fun fakeWord(): String {
        val length = random.nextInt(MIN_WORD_LENGTH, MAX_WORD_LENGTH + 1)
        return buildString { repeat(length) { append(('a'..'z').random(random)) } }
    }

    public companion object {
        /** Default cap on how deep reflective construction recurses into nested data classes. */
        public const val DEFAULT_MAX_DEPTH: Int = 4

        private const val MAX_COLLECTION_SIZE = 3
        private const val MIN_WORD_LENGTH = 4
        private const val MAX_WORD_LENGTH = 8
        private const val PHONE_DIGITS = 10

        // 2000-01-01T00:00:00Z, the fixed anchor around which buildFakeTime scatters values so that
        // its output depends only on the seed, never on the wall clock.
        private const val REFERENCE_EPOCH_SECONDS = 946_684_800L
        private const val HUNDRED_YEARS_SECONDS = 100L * 365 * 24 * 3600
    }
}
