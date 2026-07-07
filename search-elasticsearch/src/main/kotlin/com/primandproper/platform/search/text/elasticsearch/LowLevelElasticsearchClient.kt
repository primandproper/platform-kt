package com.primandproper.platform.search.text.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.http.Header
import org.apache.http.HttpHost
import org.apache.http.message.BasicHeader
import org.elasticsearch.client.Request
import org.elasticsearch.client.ResponseException
import org.elasticsearch.client.RestClient
import java.util.Base64

/**
 * The production [ElasticsearchClient], adapting the Elastic JVM client's low-level REST transport
 * (`org.elasticsearch.client.RestClient`, bundled with `co.elastic.clients:elasticsearch-java`). The
 * low-level client speaks raw JSON over HTTP, which lines up exactly with this backend's string
 * boundary, so no typed request/response classes are involved.
 *
 * Each REST call blocks, so it is dispatched onto [Dispatchers.IO] and awaited from the `suspend`
 * method. The transport is built lazily on first use (under a [Mutex]) rather than in the constructor,
 * so constructing an [ElasticsearchIndexManager] — and `provideTextIndex` — never blocks on, or
 * requires, a reachable cluster; this matches platform-go's lazily-connecting client.
 *
 * TODO(tls): platform-go's config also carries a CA certificate (`Config.CACert`) for verifying a
 * TLS-secured cluster. Wiring that PEM into the REST client's SSLContext is a documented seam; the
 * primary adapter targets an `http://` or already-trusted `https://` address with optional basic auth.
 */
public class LowLevelElasticsearchClient(
    private val config: ElasticsearchConfig,
) : ElasticsearchClient, AutoCloseable {
    private val mutex = Mutex()
    private val mapper = ObjectMapper()

    @Volatile
    private var restClient: RestClient? = null

    private suspend fun client(): RestClient {
        restClient?.let { return it }
        return mutex.withLock {
            restClient?.let { return it }
            val host = HttpHost.create(config.address)
            val builder = RestClient.builder(host)
            config.username?.let { user ->
                val token = Base64.getEncoder().encodeToString("$user:${config.password ?: ""}".toByteArray())
                builder.setDefaultHeaders(arrayOf<Header>(BasicHeader("Authorization", "Basic $token")))
            }
            builder.build().also { restClient = it }
        }
    }

    override suspend fun indexExists(index: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                client().performRequest(Request("HEAD", "/$index")).statusLine.statusCode == 200
            } catch (e: ResponseException) {
                if (e.response.statusLine.statusCode == 404) false else throw e
            }
        }

    override suspend fun createIndex(index: String) {
        withContext(Dispatchers.IO) {
            client().performRequest(Request("PUT", "/$index"))
        }
    }

    override suspend fun indexDocument(
        index: String,
        id: String,
        documentJson: String,
    ) {
        withContext(Dispatchers.IO) {
            val request = Request("PUT", "/$index/_doc/$id")
            request.setJsonEntity(documentJson)
            client().performRequest(request)
        }
    }

    override suspend fun search(
        index: String,
        queryJson: String,
    ): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request("POST", "/$index/_search")
            request.setJsonEntity(queryJson)
            val response = client().performRequest(request)
            val root = response.entity.content.use { mapper.readTree(it) }
            root.path("hits").path("hits").map { hit -> mapper.writeValueAsString(hit.path("_source")) }
        }

    override suspend fun delete(
        index: String,
        id: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                client().performRequest(Request("DELETE", "/$index/_doc/$id"))
            } catch (e: ResponseException) {
                // A delete targeting an absent document returns 404; treat as success (idempotent),
                // matching platform-go's "not_found is a no-op" handling.
                if (e.response.statusLine.statusCode != 404) throw e
            }
        }
    }

    override suspend fun deleteByQuery(
        index: String,
        queryJson: String,
    ) {
        withContext(Dispatchers.IO) {
            val request = Request("POST", "/$index/_delete_by_query")
            request.addParameter("refresh", "true")
            request.setJsonEntity(queryJson)
            client().performRequest(request)
        }
    }

    override suspend fun ping(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                client().performRequest(Request("GET", "/")).statusLine.statusCode == 200
            } catch (e: ResponseException) {
                false
            }
        }

    override fun close() {
        restClient?.close()
    }
}
