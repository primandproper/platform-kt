package com.primandproper.platform.version

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionTest {
    // BuildInfo is process-global (the analog of Go's package vars). Snapshot and restore it around
    // every test so mutations don't leak between cases, mirroring the Go test's t.Cleanup.
    private var saved: List<String?> = emptyList()

    @BeforeTest
    fun snapshot() {
        saved = listOf(BuildInfo.version, BuildInfo.commitHash, BuildInfo.commitTime, BuildInfo.buildTime)
    }

    @AfterTest
    fun restore() {
        BuildInfo.version = saved[0]
        BuildInfo.commitHash = saved[1]
        BuildInfo.commitTime = saved[2]
        BuildInfo.buildTime = saved[3]
    }

    private fun clearAll() {
        BuildInfo.version = null
        BuildInfo.commitHash = null
        BuildInfo.commitTime = null
        BuildInfo.buildTime = null
    }

    @Test
    fun getReturnsUnknownWhenUnset() {
        clearAll()
        val info = get()
        assertEquals("unknown", info.version)
        assertEquals("unknown", info.commitHash)
        assertEquals("unknown", info.commitTime)
        assertEquals("unknown", info.buildTime)
    }

    @Test
    fun getTreatsEmptyStringAsUnset() {
        clearAll()
        BuildInfo.version = ""
        assertEquals("unknown", get().version)
    }

    @Test
    fun getReturnsSetValues() {
        BuildInfo.version = "v1.2.3"
        BuildInfo.commitHash = "abc123"
        BuildInfo.commitTime = "2026-01-01T00:00:00Z"
        BuildInfo.buildTime = "2026-01-02T00:00:00Z"

        val info = get()
        assertEquals("v1.2.3", info.version)
        assertEquals("abc123", info.commitHash)
        assertEquals("2026-01-01T00:00:00Z", info.commitTime)
        assertEquals("2026-01-02T00:00:00Z", info.buildTime)
    }

    @Test
    fun toJsonMatchesGoIndentedEncoder() {
        BuildInfo.version = "v9.9.9"
        BuildInfo.commitHash = "deadbeef"
        BuildInfo.commitTime = "2026-03-03T00:00:00Z"
        BuildInfo.buildTime = "2026-03-04T00:00:00Z"

        val expected =
            """
            {
              "version": "v9.9.9",
              "commit_hash": "deadbeef",
              "commit_time": "2026-03-03T00:00:00Z",
              "build_time": "2026-03-04T00:00:00Z"
            }
            """.trimIndent() + "\n"

        assertEquals(expected, get().toJson())
    }

    @Test
    fun toJsonRendersUnknownFallback() {
        clearAll()
        val json = get().toJson()
        assertTrue(json.contains("\"version\": \"unknown\""))
        assertTrue(json.contains("\"build_time\": \"unknown\""))
    }

    @Test
    fun writeJsonWritesTheSameContentAsGet() {
        BuildInfo.version = "v5.0.0"
        BuildInfo.commitHash = "cafe"
        BuildInfo.commitTime = "2026-05-05T00:00:00Z"
        BuildInfo.buildTime = "2026-05-06T00:00:00Z"

        val sb = StringBuilder()
        writeJson(sb)
        assertEquals(get().toJson(), sb.toString())
    }

    @Test
    fun toJsonEscapesSpecialCharacters() {
        clearAll()
        BuildInfo.version = "a\"b\\c"
        assertTrue(get().toJson().contains("\"version\": \"a\\\"b\\\\c\""))
    }
}
