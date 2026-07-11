package com.primandproper.platform.search.text.elasticsearch

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.TracerProvider
import com.primandproper.platform.search.text.DocumentCodec
import com.primandproper.platform.search.text.Index
import com.primandproper.platform.search.text.config.TextSearchConfig
import com.primandproper.platform.search.text.config.TextSearchProvider
import com.primandproper.platform.search.text.noop.NoopIndex

/**
 * Builds a text [Index] for the configured provider. Port of platform-go's
 * `textsearchcfg.ProvideIndex`.
 *
 * This factory lives in `:search-elasticsearch` rather than `:search-api` because it unites the API
 * contract with a concrete backend — wiring it in the API module would force a dependency cycle. It is
 * the analog of Go's `search/text/config` package sitting above the backend subpackages, and mirrors
 * how `:cache-redis` owns `provideCache`.
 *
 * It is `suspend` because the Elasticsearch backend runs [ElasticsearchIndexManager.ensureIndices]
 * (an index existence-check + create) before returning, exactly as Go's `ProvideIndexManager` does.
 *
 * @param config the chosen provider.
 * @param codec turns documents into their stored JSON form and back; used only by real backends.
 * @param indexName the target index name.
 * @param elasticsearchConfig required when [TextSearchConfig.provider] is
 *   [TextSearchProvider.ELASTICSEARCH] and no [client] override is supplied; ignored otherwise.
 * @param client an override [ElasticsearchClient] (a fake in tests); when `null` a lazily-connecting
 *   [LowLevelElasticsearchClient] is built from [elasticsearchConfig].
 *
 * TODO(algolia): platform-go also dispatches [TextSearchProvider.ALGOLIA] to a real
 * `search/text/algolia` backend. That backend is out of scope for this port; the provider is accepted
 * but falls back to a [NoopIndex], the same safe default Go uses for an unknown provider. Port the
 * Algolia backend into a `:search-algolia` module and extend this dispatch when it lands.
 *
 * TODO(circuitbreaking): Go additionally builds a `circuitbreaking.CircuitBreaker` from the config and
 * threads it into the backend — see the seam on [ElasticsearchIndexManager].
 */
public suspend fun <T : Any> provideTextIndex(
    config: TextSearchConfig,
    codec: DocumentCodec<T>,
    indexName: String,
    elasticsearchConfig: ElasticsearchConfig? = null,
    client: ElasticsearchClient? = null,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
): Index<T> =
    when (config.provider) {
        TextSearchProvider.ELASTICSEARCH -> {
            val engineClient =
                client ?: LowLevelElasticsearchClient(
                    requireNotNull(elasticsearchConfig) { "elasticsearch provider requires an ElasticsearchConfig" },
                )
            ElasticsearchIndexManager(engineClient, codec, indexName, logger, tracerProvider).also { it.ensureIndices() }
        }
        TextSearchProvider.ALGOLIA -> NoopIndex()
    }
