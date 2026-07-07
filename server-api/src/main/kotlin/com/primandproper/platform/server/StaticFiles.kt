package com.primandproper.platform.server

import com.primandproper.platform.routing.HttpHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * Builds a [HttpHandler] that serves static files from [assetsDir]. Direct port of platform-go's
 * `server/http.RootLevelAssetsHandler`: it serves only root-level files (no subdirectories) and
 * guards against path traversal by confirming the resolved path stays inside [assetsDir]. Register it
 * as the last route (e.g. `router.get("/{path}", rootLevelAssetsHandler(dir))`).
 *
 * File IO is dispatched to [Dispatchers.IO] so the blocking reads don't stall the serving coroutine —
 * the Kotlin-native counterpart to Go's `http.FileServer` running on its own goroutine.
 */
public fun rootLevelAssetsHandler(assetsDir: String): HttpHandler {
    val assetsRoot = File(assetsDir).canonicalFile

    return HttpHandler { call ->
        val requestPath = call.path

        // Only serve root-level files: reject anything with a nested path segment.
        if (requestPath.removePrefix("/").contains("/")) {
            call.respondStatus(NOT_FOUND)
            return@HttpHandler
        }

        val served =
            withContext(Dispatchers.IO) {
                val candidate = File(assetsRoot, requestPath.removePrefix("/")).canonicalFile

                // Guard against traversal: the resolved file must stay within the assets root.
                val withinRoot = candidate.path == assetsRoot.path || candidate.path.startsWith(assetsRoot.path + File.separator)
                if (!withinRoot || !candidate.isFile) {
                    null
                } else {
                    val contentType = Files.probeContentType(candidate.toPath()) ?: DEFAULT_CONTENT_TYPE
                    contentType to candidate.readBytes()
                }
            }

        if (served == null) {
            call.respondStatus(NOT_FOUND)
        } else {
            val (contentType, bytes) = served
            call.respondBytes(OK, contentType, bytes)
        }
    }
}

private const val OK = 200
private const val NOT_FOUND = 404
private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
