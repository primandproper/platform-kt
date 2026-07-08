package com.primandproper.platform.search.text.elasticsearch

/**
 * An in-memory [ElasticsearchClient] fake, standing in for a live cluster so
 * [ElasticsearchIndexManager] logic can be unit-tested — the analog of the httptest-server-backed
 * clients in platform-go's `elasticsearch_test.go`. Records the request bodies it received so tests
 * can assert the query JSON the manager built.
 */
class FakeElasticsearchClient(
    var failOn: String? = null,
    private val existingIndices: MutableSet<String> = mutableSetOf(),
) : ElasticsearchClient {
    /** Stored documents, keyed by "index/id" → document JSON. */
    val documents: MutableMap<String, String> = linkedMapOf()

    /** Canned `_source` bodies returned by [search], in ranking order. */
    var searchHits: List<String> = emptyList()

    val createdIndices: MutableList<String> = mutableListOf()
    val searchBodies: MutableList<String> = mutableListOf()
    val deleteByQueryBodies: MutableList<String> = mutableListOf()

    private fun maybeFail(op: String) {
        if (failOn == op) throw RuntimeException("injected $op failure")
    }

    override suspend fun indexExists(index: String): Boolean {
        maybeFail("indexExists")
        return index in existingIndices
    }

    override suspend fun createIndex(index: String) {
        maybeFail("createIndex")
        existingIndices += index
        createdIndices += index
    }

    override suspend fun indexDocument(
        index: String,
        id: String,
        documentJson: String,
    ) {
        maybeFail("indexDocument")
        documents["$index/$id"] = documentJson
    }

    override suspend fun search(
        index: String,
        queryJson: String,
    ): List<String> {
        maybeFail("search")
        searchBodies += queryJson
        return searchHits
    }

    override suspend fun delete(
        index: String,
        id: String,
    ) {
        maybeFail("delete")
        documents.remove("$index/$id")
    }

    override suspend fun deleteByQuery(
        index: String,
        queryJson: String,
    ) {
        maybeFail("deleteByQuery")
        deleteByQueryBodies += queryJson
        documents.keys.filter { it.startsWith("$index/") }.forEach { documents.remove(it) }
    }

    override suspend fun ping(): Boolean {
        maybeFail("ping")
        return true
    }
}
