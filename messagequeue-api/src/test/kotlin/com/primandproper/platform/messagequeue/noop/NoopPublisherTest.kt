package com.primandproper.platform.messagequeue.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Port of platform-go's `messagequeue/noop` publisher tests. */
class NoopPublisherTest {
    @Test
    fun `provider hands out a publisher, pings, and closes`() =
        runTest {
            val provider = NoopPublisherProvider<String>()
            assertNotNull(provider.publisher("topic"))
            provider.ping()
            provider.close()
        }

    @Test
    fun `publisher discards every publish and close`() =
        runTest {
            val publisher = NoopPublisher<String>()
            publisher.publish("data")
            publisher.publishAsync("data")
            publisher.close()
        }
}
