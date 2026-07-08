package com.primandproper.platform.messagequeue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MessageQueueConfigTest {
    private fun validQueues() =
        QueuesConfig(
            dataChangesTopicName = "data-changes",
            outboundEmailsTopicName = "outbound-emails",
            searchIndexRequestsTopicName = "search-index-requests",
            mobileNotificationsTopicName = "mobile-notifications",
            userDataAggregationTopicName = "user-data-aggregation",
            webhookExecutionRequestsTopicName = "webhook-execution-requests",
        )

    @Test
    fun `fromValue resolves a known provider case-insensitively and trimmed`() {
        assertEquals(MessageQueueProvider.REDIS, MessageQueueProvider.fromValue("  Redis "))
        assertEquals(MessageQueueProvider.KAFKA, MessageQueueProvider.fromValue("KAFKA"))
        assertEquals(MessageQueueProvider.PUBSUB, MessageQueueProvider.fromValue("pubsub"))
        assertEquals(MessageQueueProvider.SQS, MessageQueueProvider.fromValue("sqs"))
    }

    @Test
    fun `fromValue returns null for an unknown provider`() {
        assertNull(MessageQueueProvider.fromValue("rabbitmq"))
        assertNull(MessageQueueProvider.fromValue(""))
    }

    @Test
    fun `a fully populated QueuesConfig validates`() {
        validQueues().validate()
    }

    @Test
    fun `a QueuesConfig missing a name fails validation naming it`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                validQueues().copy(outboundEmailsTopicName = " ").validate()
            }
        assertEquals(true, error.message?.contains("outboundEmailsTopicName"))
    }
}
