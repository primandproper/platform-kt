package com.primandproper.platform.httpclient

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RetryTest {
    @Test
    fun `no hook runs a single attempt`() =
        runTest {
            val cfg = HttpClientConfig()
            var calls = 0
            val response =
                cfg.executeWithRetries(HttpRequest.get("https://x")) {
                    calls++
                    HttpResponse(statusCode = 200)
                }
            assertEquals(1, calls)
            assertEquals(200, response.statusCode)
        }

    @Test
    fun `hook retries until it returns null`() =
        runTest {
            val cfg =
                HttpClientConfig {
                    retryHook =
                        RetryHook { attempt, _, outcome ->
                            val status = (outcome as HttpOutcome.Received).response.statusCode
                            if (status == 503 && attempt < 3) Duration.ZERO else null
                        }
                }
            var calls = 0
            val response =
                cfg.executeWithRetries(HttpRequest.get("https://x")) {
                    calls++
                    if (calls < 3) HttpResponse(statusCode = 503) else HttpResponse(statusCode = 200)
                }
            assertEquals(3, calls)
            assertEquals(200, response.statusCode)
        }

    @Test
    fun `hook can retry a thrown failure then rethrow`() =
        runTest {
            val cfg =
                HttpClientConfig {
                    retryHook =
                        RetryHook { attempt, _, outcome ->
                            if (outcome is HttpOutcome.Failed && attempt < 2) 1.milliseconds else null
                        }
                }
            var calls = 0
            assertFailsWith<IllegalStateException> {
                cfg.executeWithRetries(HttpRequest.get("https://x")) {
                    calls++
                    throw IllegalStateException("boom")
                }
            }
            assertEquals(2, calls)
        }
}
