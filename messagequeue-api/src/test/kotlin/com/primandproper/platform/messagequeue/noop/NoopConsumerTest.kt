package com.primandproper.platform.messagequeue.noop

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Port of platform-go's `messagequeue/noop` consumer tests. */
class NoopConsumerTest {
    @Test
    fun `provider hands out a consumer and closes`() =
        runTest {
            val provider = NoopConsumerProvider
            val consumer = provider.consumer("topic") {}
            assertNotNull(consumer)
            provider.close()
        }

    @Test
    fun `consume returns immediately`() =
        runTest {
            NoopConsumer.consume()
        }

    @Test
    fun `messages is an empty flow`() =
        runTest {
            assertTrue(NoopConsumer.messages().toList().isEmpty())
        }
}
