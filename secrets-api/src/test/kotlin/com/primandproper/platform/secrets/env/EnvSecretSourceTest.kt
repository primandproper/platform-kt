package com.primandproper.platform.secrets.env

import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.secrets.SecretNotFoundException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Port of `secrets/env/env_test.go`. The Go metrics-wiring cases (`with error creating lookup
 * counter` / `latency histogram`) don't translate — the Kotlin observability surface records on
 * spans, with a `TODO(metrics)` seam rather than a separate metrics provider — so the meaningful
 * `GetSecret` / `Close` behavior is what carries over.
 */
class EnvSecretSourceTest {
    /** Builds an [EnvSecretSource] over a [RecordingObserver] and an in-memory env map. */
    private fun recordingSource(env: Map<String, String>): Pair<EnvSecretSource, RecordingObserver> {
        val obs = RecordingObserver()
        val source = EnvSecretSource(obs) { env[it] }
        return source to obs
    }

    @Test
    fun `returns set env var`() =
        runTest {
            val (source, obs) = recordingSource(mapOf("TEST_SECRET" to "secret-value"))

            val got = source.getSecret("TEST_SECRET")

            assertEquals("secret-value", got)

            // The lookup key is observed; the secret value must never be.
            obs.assertObservedOperationWithValues("secret_key" to "TEST_SECRET")
            val op = obs.operations.single()
            assertTrue(
                op.observations.none { it.value == "secret-value" },
                "secret value must never be observed on any pillar",
            )
        }

    @Test
    fun `errors for unset env var`() =
        runTest {
            val (source, _) = recordingSource(emptyMap())

            val ex = assertFailsWith<SecretNotFoundException> { source.getSecret("MISSING_KEY") }
            assertEquals("MISSING_KEY", ex.key)
        }

    @Test
    fun `returns empty for set-but-empty env var`() =
        runTest {
            val (source, _) = recordingSource(mapOf("EMPTY_KEY" to ""))

            val got = source.getSecret("EMPTY_KEY")

            assertEquals("", got)
        }

    @Test
    fun `close is a no-op`() {
        val (source, _) = recordingSource(emptyMap())
        source.close()
    }

    @Test
    fun `public constructor reads real process environment`() =
        runTest {
            // PATH is set in essentially every environment; assert it is read without erroring.
            val real = EnvSecretSource()
            val path = System.getenv("PATH")
            if (path != null) {
                assertEquals(path, real.getSecret("PATH"))
            }
        }
}
