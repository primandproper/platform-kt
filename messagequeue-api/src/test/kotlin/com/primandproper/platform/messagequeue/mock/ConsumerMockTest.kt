package com.primandproper.platform.messagequeue.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConsumerMockTest {
    @Test
    fun `it records consume calls`() =
        runTest {
            val mock = ConsumerMock(consumeFunc = {})
            mock.consume()
            assertEquals(1, mock.consumeCalls.size)
        }

    @Test
    fun `an unmocked method throws`() =
        runTest {
            assertFailsWith<IllegalStateException> { ConsumerMock().consume() }
        }

    @Test
    fun `the provider mock records provideConsumer and close`() =
        runTest {
            val consumer = ConsumerMock(consumeFunc = {})
            val mock =
                ConsumerProviderMock(
                    closeFunc = {},
                    provideConsumerFunc = { _, _ -> consumer },
                )
            mock.provideConsumer("topic") {}
            mock.close()

            assertEquals(1, mock.provideConsumerCalls.size)
            assertEquals("topic", mock.provideConsumerCalls.first().first)
            assertEquals(1, mock.closeCalls.size)
        }
}
