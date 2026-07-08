package com.primandproper.platform.random

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlicesTest {
    private val exampleList =
        listOf(
            "The", "FitnessGram™", "Pacer", "Test", "is", "a", "multistage", "aerobic", "capacity",
            "test", "that", "progressively", "gets", "more", "difficult", "as", "it", "continues.",
        )

    @Test
    fun `randomElementOrNull returns a member of the receiver`() {
        assertTrue(exampleList.contains(exampleList.randomElementOrNull()))
    }

    @Test
    fun `randomElementOrNull with SecureKotlinRandom still returns a member of the receiver`() {
        assertTrue(exampleList.contains(exampleList.randomElementOrNull(SecureKotlinRandom)))
    }

    @Test
    fun `randomElementOrNull returns null for an empty list instead of a Go-style zero value`() {
        assertNull(emptyList<String>().randomElementOrNull())
        assertNull(emptyList<Int>().randomElementOrNull())
    }

    @Test
    fun `secureShuffled returns a permutation of the same elements`() {
        val shuffled = exampleList.secureShuffled()

        assertTrue(shuffled.size == exampleList.size)
        assertTrue(shuffled.toSet() == exampleList.toSet())
    }
}
