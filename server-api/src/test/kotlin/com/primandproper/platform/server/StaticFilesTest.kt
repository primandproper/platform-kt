package com.primandproper.platform.server

import com.primandproper.platform.routing.HttpMethod
import com.primandproper.platform.routing.RoutingCall
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** A [RoutingCall] fake that records the response and serves a fixed request path. */
private class RecordingCall(
    override val path: String,
) : RoutingCall {
    var status: Int? = null
    var body: ByteArray? = null

    override val method: HttpMethod = HttpMethod.GET
    override val requestId: String? = null

    override fun pathParameter(key: String): String? = null

    override fun header(name: String): String? = null

    override suspend fun respondText(
        status: Int,
        text: String,
    ) {
        this.status = status
        this.body = text.toByteArray()
    }

    override suspend fun respondBytes(
        status: Int,
        contentType: String,
        bytes: ByteArray,
    ) {
        this.status = status
        this.body = bytes
    }

    override suspend fun respondStatus(status: Int) {
        this.status = status
    }
}

/** Covers the traversal guard and root-only serving from platform-go's `server/http/static_files_test.go`. */
class StaticFilesTest {
    private fun tempAssets(): File {
        val dir = Files.createTempDirectory("assets").toFile()
        File(dir, "robots.txt").writeText("User-agent: *")
        File(dir, "nested").mkdirs()
        File(dir, "nested/secret.txt").writeText("hidden")
        return dir
    }

    @Test
    fun `serves a root-level file`() =
        runTest {
            val handler = rootLevelAssetsHandler(tempAssets().path)
            val call = RecordingCall("/robots.txt")

            handler.handle(call)

            assertEquals(200, call.status)
            assertEquals("User-agent: *", call.body?.decodeToString())
        }

    @Test
    fun `refuses a nested path`() =
        runTest {
            val handler = rootLevelAssetsHandler(tempAssets().path)
            val call = RecordingCall("/nested/secret.txt")

            handler.handle(call)

            assertEquals(404, call.status)
        }

    @Test
    fun `refuses a traversal attempt`() =
        runTest {
            val handler = rootLevelAssetsHandler(tempAssets().path)
            val call = RecordingCall("/..")

            handler.handle(call)

            assertEquals(404, call.status)
        }

    @Test
    fun `returns 404 for a missing file`() =
        runTest {
            val handler = rootLevelAssetsHandler(tempAssets().path)
            val call = RecordingCall("/nope.txt")

            handler.handle(call)

            assertEquals(404, call.status)
        }
}
