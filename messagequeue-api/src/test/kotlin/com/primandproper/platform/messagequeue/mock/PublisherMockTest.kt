package com.primandproper.platform.messagequeue.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublisherMockTest {
    @Test
    fun `it records publish, publishAsync, and stop calls`() =
        runTest {
            val mock = PublisherMock(publishFunc = {}, publishAsyncFunc = {}, stopFunc = {})
            mock.publish("a")
            mock.publishAsync("b")
            mock.stop()

            assertEquals(listOf<Any>("a"), mock.publishCalls)
            assertEquals(listOf<Any>("b"), mock.publishAsyncCalls)
            assertEquals(1, mock.stopCalls.size)
        }

    @Test
    fun `an unmocked method throws`() =
        runTest {
            assertFailsWith<IllegalStateException> { PublisherMock().publish("x") }
        }

    @Test
    fun `the provider mock records providePublisher, ping, and close`() =
        runTest {
            val published = PublisherMock(publishFunc = {})
            val mock =
                PublisherProviderMock(
                    closeFunc = {},
                    pingFunc = {},
                    providePublisherFunc = { published },
                )
            mock.providePublisher("topic")
            mock.ping()
            mock.close()

            assertEquals(listOf("topic"), mock.providePublisherCalls)
            assertEquals(1, mock.pingCalls.size)
            assertEquals(1, mock.closeCalls.size)
        }
}
