package com.primandproper.platform.llm.anthropic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Mirrors platform-go's `llm/anthropic/config_test.go`. */
class AnthropicConfigTest {
    @Test
    fun `validate accepts a non-empty key`() {
        AnthropicConfig(apiKey = "k").validate()
    }

    @Test
    fun `validate rejects an empty key`() {
        assertFailsWith<EmptyApiKeyException> { AnthropicConfig(apiKey = "").validate() }
    }

    @Test
    fun `defaults target the most capable model and Anthropic's host`() {
        val config = AnthropicConfig(apiKey = "k")
        assertEquals("claude-opus-4-8", config.defaultModel)
        assertEquals(AnthropicModels.OPUS_4_8, config.defaultModel)
        assertEquals("https://api.anthropic.com", config.baseUrl)
        assertEquals(4096, config.maxTokens)
    }

    @Test
    fun `model constants carry the current Claude ids`() {
        assertEquals("claude-opus-4-8", AnthropicModels.OPUS_4_8)
        assertEquals("claude-sonnet-5", AnthropicModels.SONNET_5)
        assertEquals("claude-haiku-4-5-20251001", AnthropicModels.HAIKU_4_5)
        assertEquals(AnthropicModels.OPUS_4_8, AnthropicModels.DEFAULT)
    }
}
