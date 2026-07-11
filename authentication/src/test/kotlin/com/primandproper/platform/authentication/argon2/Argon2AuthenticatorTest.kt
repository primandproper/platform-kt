package com.primandproper.platform.authentication.argon2

import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The exact password + encoded hash from platform-go's argon2 tests. Because BouncyCastle computes
// the same standard Argon2id x/crypto does, this known-answer vector must verify here byte-for-byte.
private const val EXAMPLE_PASSWORD = "Pa\$\$w0rdPa\$\$w0rdPa\$\$w0rdPa\$\$w0rd"
private const val ARGON2_HASHED_EXAMPLE =
    "\$argon2id\$v=19\$m=65536,t=1,p=2\$C+YWiNi21e94acF3ip8UGA\$Ru6oL96HZSP7cVcfAbRwOuK9+vwBo/BLhCzOrGrMH0M"

class Argon2AuthenticatorTest {
    @Test
    fun `known-answer hash verifies`() =
        runTest {
            val authenticator = newArgon2Authenticator()
            assertTrue(authenticator.passwordMatches(ARGON2_HASHED_EXAMPLE, EXAMPLE_PASSWORD))
        }

    @Test
    fun `non-matching password returns false with no error`() =
        runTest {
            val authenticator = newArgon2Authenticator()
            assertFalse(authenticator.passwordMatches(ARGON2_HASHED_EXAMPLE, "wrongPassword"))
        }

    @Test
    fun `malformed hash throws`() =
        runTest {
            val authenticator = newArgon2Authenticator()
            assertFailsWith<IllegalArgumentException> {
                authenticator.passwordMatches("       blah blah blah not a valid hash lol           ", EXAMPLE_PASSWORD)
            }
        }

    @Test
    fun `rejects a hash whose memory parameter exceeds the cap`() =
        runTest {
            val authenticator = newArgon2Authenticator()
            // A crafted hash asking for ~2 TiB of memory must be rejected before any derivation runs,
            // rather than handed to BouncyCastle where it would exhaust the host.
            val hostileHash =
                "\$argon2id\$v=19\$m=2000000000,t=1,p=2\$C+YWiNi21e94acF3ip8UGA\$Ru6oL96HZSP7cVcfAbRwOuK9+vwBo/BLhCzOrGrMH0M"
            assertFailsWith<IllegalArgumentException> {
                authenticator.passwordMatches(hostileHash, EXAMPLE_PASSWORD)
            }
        }

    @Test
    fun `rejects a hash whose iterations parameter exceeds the cap`() =
        runTest {
            val authenticator = newArgon2Authenticator()
            val hostileHash =
                "\$argon2id\$v=19\$m=65536,t=1000000,p=2\$C+YWiNi21e94acF3ip8UGA\$Ru6oL96HZSP7cVcfAbRwOuK9+vwBo/BLhCzOrGrMH0M"
            assertFailsWith<IllegalArgumentException> {
                authenticator.passwordMatches(hostileHash, EXAMPLE_PASSWORD)
            }
        }

    @Test
    fun `hash then verify round-trips`() =
        runTest {
            val authenticator = newArgon2Authenticator()

            val encoded = authenticator.hashPassword(EXAMPLE_PASSWORD)
            // parallelism is CPU-clamped to [2, 255], so pin it to the actual value rather than
            // assuming the floor of 2 (this machine may have more cores).
            assertTrue(encoded.startsWith("\$argon2id\$v=19\$m=65536,t=1,p=$parallelism\$"))
            assertTrue(authenticator.passwordMatches(encoded, EXAMPLE_PASSWORD))
            assertFalse(authenticator.passwordMatches(encoded, "not-the-password"))
        }

    @Test
    fun `hashing the same password twice yields distinct salted hashes`() =
        runTest {
            val authenticator = newArgon2Authenticator()
            val first = authenticator.hashPassword(EXAMPLE_PASSWORD)
            val second = authenticator.hashPassword(EXAMPLE_PASSWORD)
            assertTrue(first != second, "random salt must make each hash unique")
        }

    @Test
    fun `parallelism is clamped to a valid non-zero range`() {
        assertTrue(parallelism in 2..255)
    }

    @Test
    fun `hashPassword records cost parameters and ends the operation`() =
        runTest {
            val observer = RecordingObserver()
            val authenticator = newArgon2Authenticator(observer)

            authenticator.hashPassword(EXAMPLE_PASSWORD)

            observer.assertObservedOperationWithValues(
                "argon2.memory" to 65536,
                "argon2.iterations" to 1,
                "argon2.key_length" to 32,
            )
            assertEquals(1, observer.operations.size)
            assertTrue(observer.operations[0].ended)
        }

    @Test
    fun `passwordMatches records cost parameters and ends the operation`() =
        runTest {
            val observer = RecordingObserver()
            val authenticator = newArgon2Authenticator(observer)

            assertTrue(authenticator.passwordMatches(ARGON2_HASHED_EXAMPLE, EXAMPLE_PASSWORD))

            observer.assertObservedOperationWithValues("argon2.memory" to 65536)
            assertEquals(1, observer.operations.size)
            assertTrue(observer.operations[0].ended)
        }
}
