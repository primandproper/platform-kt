package com.primandproper.platform.fake

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val SEED = 1234L

private enum class Color { RED, GREEN, BLUE }

private data class Example(val name: String, val age: Int)

private data class Person(
    val fullName: String,
    val email: String,
    val age: Int,
    val active: Boolean,
    val createdAt: Instant,
    val id: UUID,
    val tags: List<String>,
    val favorite: Color,
)

class FakeTest {
    @Test
    fun buildFakeTimeIsNotTheZeroInstant() {
        assertNotEquals(Instant.EPOCH, Fake().buildFakeTime())
    }

    @Test
    fun buildFakeTimeIsTruncatedToSeconds() {
        assertEquals(0, Fake(SEED).buildFakeTime().nano, "time must carry no sub-second precision")
    }

    @Test
    fun buildFakeStringSucceedsWithANonEmptyValue() {
        val result = Fake(SEED).buildFake<String>()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isNotEmpty())
    }

    @Test
    fun buildFakeAnyFails() {
        assertTrue(Fake(SEED).buildFake<Any>().isFailure, "an unconstructable type must produce a failure")
    }

    @Test
    fun mustBuildFakeAnyThrows() {
        assertFailsWith<IllegalArgumentException> { Fake(SEED).mustBuildFake<Any>() }
    }

    @Test
    fun mustBuildFakeFillsEveryField() {
        val actual = Fake(SEED).mustBuildFake<Example>()
        assertNotEquals("", actual.name, "string field must be populated")
        assertNotEquals(0, actual.age, "int field must be populated")
    }

    @Test
    fun buildFakeForTestReturnsAWellFormedInstance() {
        val person = Fake(SEED).buildFakeForTest<Person>()

        assertTrue(person.fullName.isNotEmpty())
        assertTrue("@" in person.email, "an 'email' field must be shaped like an email")
        assertTrue(person.tags.isNotEmpty(), "list fields must be filled with at least one element")
        assertTrue(person.tags.all { it.isNotEmpty() })
        assertTrue(person.favorite in Color.entries, "enum field must be a valid constant")
    }

    @Test
    fun aFixedSeedYieldsReproducibleTimes() {
        assertEquals(Fake(SEED).buildFakeTime(), Fake(SEED).buildFakeTime())
    }

    @Test
    fun aFixedSeedYieldsReproducibleObjects() {
        assertEquals(
            Fake(SEED).mustBuildFake<Person>(),
            Fake(SEED).mustBuildFake<Person>(),
            "two generators with the same seed must produce identical data",
        )
    }

    @Test
    fun differentSeedsGenerallyYieldDifferentObjects() {
        assertNotEquals(
            Fake(SEED).mustBuildFake<Person>(),
            Fake(SEED + 1).mustBuildFake<Person>(),
        )
    }

    @Test
    fun primitivesAreWellFormed() {
        val fake = Fake(SEED)
        assertTrue(fake.mustBuildFake<UUID>().toString().isNotEmpty())
        // Bounded types must land inside their range rather than overflow.
        assertTrue(fake.buildFake<Short>().isSuccess)
        assertTrue(fake.buildFake<Byte>().isSuccess)
        assertTrue(fake.buildFake<Char>().isSuccess)
    }
}
