package com.primandproper.platform.retry

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Mirrors platform-go's `retry/noop/noop_test.go`. */
class NoopPolicyTest {
    @Test
    fun `executes exactly once on success`() =
        runTest {
            var attempts = 0

            val result =
                NoopPolicy.execute {
                    attempts++
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(1, attempts)
        }

    @Test
    fun `executes exactly once on failure`() =
        runTest {
            var attempts = 0
            val expected = IllegalStateException("fail")

            val thrown =
                assertFailsWith<IllegalStateException> {
                    NoopPolicy.execute {
                        attempts++
                        throw expected
                    }
                }

            assertSame(expected, thrown)
            assertEquals(1, attempts)
        }
}
