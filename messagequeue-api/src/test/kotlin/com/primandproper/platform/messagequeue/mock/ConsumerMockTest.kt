package com.primandproper.platform.messagequeue.mock

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
            assertEquals(1, mock.consumeCalls)
        }

    @Test
    fun `it records messages calls and delegates to the func`() =
        runTest {
            val mock = ConsumerMock(messagesFunc = { flowOf("a".encodeToByteArray()) })
            val payloads = mock.messages().toList().map { it.decodeToString() }
            assertEquals(listOf("a"), payloads)
            assertEquals(1, mock.messagesCalls)
        }

    @Test
    fun `an unmocked method throws`() =
        runTest {
            assertFailsWith<IllegalStateException> { ConsumerMock().consume() }
            assertFailsWith<IllegalStateException> { ConsumerMock().messages() }
        }

    @Test
    fun `the provider mock records consumer and close`() =
        runTest {
            val consumer = ConsumerMock(consumeFunc = {})
            val mock =
                ConsumerProviderMock(
                    closeFunc = {},
                    consumerFunc = { _, _ -> consumer },
                )
            mock.consumer("topic") {}
            mock.close()

            assertEquals(1, mock.consumerCalls.size)
            assertEquals("topic", mock.consumerCalls.first().first)
            assertEquals(1, mock.closeCalls)
        }
}
