package com.primandproper.platform.retry

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class FlowRetryTest {
    @Test
    fun `retries collection per the backoff policy until it succeeds`() =
        runTest {
            var collections = 0

            val values =
                flow {
                    collections++
                    if (collections < 3) error("transient")
                    emit(1)
                    emit(2)
                }.retryWithPolicy {
                    maxAttempts = 5
                    initialDelay = 1.milliseconds
                    maxDelay = 10.milliseconds
                    useJitter = false
                }.toList()

            assertEquals(listOf(1, 2), values)
            assertEquals(3, collections)
        }

    @Test
    fun `gives up after maxAttempts and lets the last failure through`() =
        runTest {
            var collections = 0
            var caught: Throwable? = null

            flow<Int> {
                collections++
                error("transient")
            }.retryWithPolicy {
                maxAttempts = 3
                initialDelay = 1.milliseconds
                maxDelay = 10.milliseconds
                useJitter = false
            }.catch { caught = it }
                .toList()

            assertEquals(3, collections)
            assertEquals("transient", caught?.message)
        }

    @Test
    fun `stops immediately on an UnretryableException`() =
        runTest {
            var collections = 0
            var caught: Throwable? = null

            flow<Int> {
                collections++
                throw unretryable(IllegalStateException("fatal"))
            }.retryWithPolicy {
                maxAttempts = 5
                initialDelay = 1.milliseconds
            }.catch { caught = it }
                .toList()

            assertEquals(1, collections)
            assertTrue(caught is UnretryableException)
        }
}
