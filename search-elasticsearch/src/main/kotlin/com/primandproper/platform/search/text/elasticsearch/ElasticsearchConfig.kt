package com.primandproper.platform.search.text.elasticsearch

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Connection settings for the Elasticsearch text-search backend. Port of platform-go's
 * `elasticsearch.Config`.
 *
 * @param address the cluster address, e.g. `http://localhost:9200`.
 * @param username optional basic-auth username.
 * @param password optional basic-auth password.
 * @param caCert optional PEM CA certificate bytes for TLS verification.
 * @param indexOperationTimeout per-operation timeout applied to index writes; [ZERO] means the
 *   client default, matching Go's zero-value `time.Duration`.
 */
public data class ElasticsearchConfig(
    val address: String,
    val username: String? = null,
    val password: String? = null,
    val caCert: ByteArray? = null,
    val indexOperationTimeout: Duration = ZERO,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ElasticsearchConfig) return false
        return address == other.address &&
            username == other.username &&
            password == other.password &&
            caCert.contentEqualsNullable(other.caCert) &&
            indexOperationTimeout == other.indexOperationTimeout
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + (caCert?.contentHashCode() ?: 0)
        result = 31 * result + indexOperationTimeout.hashCode()
        return result
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    when {
        this == null && other == null -> true
        this == null || other == null -> false
        else -> this.contentEquals(other)
    }
