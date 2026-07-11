package com.primandproper.platform.analytics.mock

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventReporterMockTest {
    @Test
    fun `records calls and delegates to funcs`() =
        runTest {
            val mock =
                EventReporterMock(
                    closeFunc = {},
                    addUserFunc = { _, _ -> },
                    eventOccurredFunc = { _, _, _ -> },
                    eventOccurredAnonymousFunc = { _, _, _ -> },
                )

            mock.close()
            mock.addUser("u1", mapOf("plan" to "pro"))
            mock.eventOccurred("signup", "u1")
            mock.eventOccurredAnonymous("page_view", "anon1")

            assertEquals(1, mock.closeCalls)
            assertEquals(EventReporterMock.AddUserCall("u1", mapOf("plan" to "pro")), mock.addUserCalls.single())
            assertEquals("signup", mock.eventOccurredCalls.single().event)
            assertEquals("anon1", mock.eventOccurredAnonymousCalls.single().anonymousID)
        }

    @Test
    fun `unmocked call throws`() =
        runTest {
            val mock = EventReporterMock()
            assertFailsWith<IllegalStateException> { mock.addUser("u1") }
        }
}
