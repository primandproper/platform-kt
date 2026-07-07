package com.primandproper.platform.messagequeue

/**
 * The supported message-queue providers. Port of platform-go's `msgconfig.Provider*` constants
 * (`redis`, `sqs`, `pubsub`, `kafka`). [value] is the wire/string form matched against configuration.
 */
public enum class MessageQueueProvider(
    public val value: String,
) {
    REDIS("redis"),
    SQS("sqs"),
    PUBSUB("pubsub"),
    KAFKA("kafka"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive) — the analog of Go's
         * `cleanString` before the provider switch — or `null` when it names no known provider, which
         * the selection factory treats as "fall back to noop".
         */
        public fun fromValue(value: String): MessageQueueProvider? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
        }
    }
}

/**
 * The set of well-known topic names a service publishes to and consumes from. Port of platform-go's
 * `msgconfig.QueuesConfig`; every name is required, mirroring Go's `validation.Required` on each field.
 */
public data class QueuesConfig(
    val dataChangesTopicName: String,
    val outboundEmailsTopicName: String,
    val searchIndexRequestsTopicName: String,
    val mobileNotificationsTopicName: String,
    val userDataAggregationTopicName: String,
    val webhookExecutionRequestsTopicName: String,
) {
    /**
     * Validates that every topic name is non-blank, throwing [IllegalArgumentException] naming the
     * missing ones — the analog of Go's `ValidateWithContext` requiring each field.
     */
    public fun validate() {
        val missing =
            buildList {
                if (dataChangesTopicName.isBlank()) add("dataChangesTopicName")
                if (outboundEmailsTopicName.isBlank()) add("outboundEmailsTopicName")
                if (searchIndexRequestsTopicName.isBlank()) add("searchIndexRequestsTopicName")
                if (mobileNotificationsTopicName.isBlank()) add("mobileNotificationsTopicName")
                if (userDataAggregationTopicName.isBlank()) add("userDataAggregationTopicName")
                if (webhookExecutionRequestsTopicName.isBlank()) add("webhookExecutionRequestsTopicName")
            }
        require(missing.isEmpty()) { "missing required topic names: ${missing.joinToString(", ")}" }
    }
}
