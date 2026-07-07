package com.primandproper.platform.messagequeue.noop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Port of platform-go's `messagequeue/noop` consumer tests. */
class NoopConsumerTest {
    @Test
    fun `provider hands out a consumer and closes`() =
        runTest {
            val provider = NoopConsumerProvider()
            val consumer = provider.provideConsumer("topic") {}
            assertNotNull(consumer)
            provider.close()
        }

    @Test
    fun `consume returns immediately`() =
        runTest {
            NoopConsumer().consume()
        }
}
