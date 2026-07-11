package com.primandproper.platform.messagequeue.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublisherMockTest {
    @Test
    fun `it records publish, publishAsync, and close calls`() =
        runTest {
            val mock = PublisherMock<String>(publishFunc = {}, publishAsyncFunc = {}, closeFunc = {})
            mock.publish("a")
            mock.publishAsync("b")
            mock.close()

            assertEquals(listOf("a"), mock.publishCalls)
            assertEquals(listOf("b"), mock.publishAsyncCalls)
            assertEquals(1, mock.closeCalls)
        }

    @Test
    fun `an unmocked method throws`() =
        runTest {
            assertFailsWith<IllegalStateException> { PublisherMock<String>().publish("x") }
        }

    @Test
    fun `the provider mock records publisher, ping, and close`() =
        runTest {
            val published = PublisherMock<String>(publishFunc = {})
            val mock =
                PublisherProviderMock(
                    closeFunc = {},
                    pingFunc = {},
                    publisherFunc = { published },
                )
            mock.publisher("topic")
            mock.ping()
            mock.close()

            assertEquals(listOf("topic"), mock.publisherCalls)
            assertEquals(1, mock.pingCalls)
            assertEquals(1, mock.closeCalls)
        }
}
