package com.primandproper.platform.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Mirrors the provider-selection validation in platform-go's `llm/config/config_test.go`. */
class LlmConfigTest {
    @Test
    fun `an empty provider is permitted for the noop fallback`() {
        LlmConfig(provider = "").validate()
        LlmConfig(provider = "   ").validate()
    }

    @Test
    fun `a known provider validates, case-insensitively and trimmed`() {
        LlmConfig(provider = "anthropic").validate()
        LlmConfig(provider = " OpenAI ").validate()
    }

    @Test
    fun `an unknown provider is rejected`() {
        assertFailsWith<InvalidLlmProviderException> { LlmConfig(provider = "gemini").validate() }
    }

    @Test
    fun `fromValue resolves known names and rejects unknown ones`() {
        assertEquals(LlmProviderKind.ANTHROPIC, LlmProviderKind.fromValue("ANTHROPIC"))
        assertEquals(LlmProviderKind.OPENAI, LlmProviderKind.fromValue(" openai "))
        assertNull(LlmProviderKind.fromValue("cohere"))
    }

    @Test
    fun `Role fromValue resolves wire strings`() {
        assertEquals(Role.USER, Role.fromValue("user"))
        assertEquals(Role.ASSISTANT, Role.fromValue(" Assistant "))
        assertNull(Role.fromValue("robot"))
    }
}
