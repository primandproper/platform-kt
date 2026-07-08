package com.primandproper.platform.secrets.android

import android.content.SharedPreferences
import com.primandproper.platform.observability.testing.RecordingObserver
import com.primandproper.platform.secrets.SecretNotFoundException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises [KeystoreSecretStore]'s logic against a hand-written in-memory [SharedPreferences] fake,
 * so the store's behavior (round-trip, missing-key → [SecretNotFoundException], the never-observe-the-
 * value guarantee) is verified without an Android device or Keystore. The real Keystore wiring lives
 * in [KeystoreSecretStore.create], which needs an instrumented/on-device test.
 */
class KeystoreSecretStoreTest {
    private fun store(): Pair<KeystoreSecretStore, RecordingObserver> {
        val obs = RecordingObserver()
        return KeystoreSecretStore(FakeSharedPreferences(), obs) to obs
    }

    @Test
    fun `put then get round-trips`() =
        runTest {
            val (s, _) = store()
            s.putSecret("api_token", "s3cr3t")
            assertEquals("s3cr3t", s.getSecret("api_token"))
        }

    @Test
    fun `get missing key throws SecretNotFound`() =
        runTest {
            val (s, _) = store()
            val ex = assertFailsWith<SecretNotFoundException> { s.getSecret("nope") }
            assertEquals("nope", ex.key)
        }

    @Test
    fun `remove deletes the secret`() =
        runTest {
            val (s, _) = store()
            s.putSecret("k", "v")
            s.removeSecret("k")
            assertFailsWith<SecretNotFoundException> { s.getSecret("k") }
        }

    @Test
    fun `empty stored value is distinct from missing`() =
        runTest {
            val (s, _) = store()
            s.putSecret("empty", "")
            assertEquals("", s.getSecret("empty"))
        }

    @Test
    fun `never observes the secret value`() =
        runTest {
            val (s, obs) = store()
            s.putSecret("api_token", "s3cr3t")
            s.getSecret("api_token")

            obs.assertObservedOperationWithValues("secret_key" to "api_token")
            assertTrue(
                obs.stream().none { it.value == "s3cr3t" },
                "secret value must never be observed on any pillar",
            )
        }

    @Test
    fun `close is a no-op`() {
        val (s, _) = store()
        s.close()
    }
}

/** Minimal in-memory [SharedPreferences] for unit tests: real behavior, no Android runtime. */
private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = (map[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = (map[key] as? MutableSet<String>) ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = (map[key] as? Int) ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = (map[key] as? Long) ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = (map[key] as? Float) ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = (map[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor = apply { pending[key!!] = value }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pending[key!!] = values }

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = apply { pending[key!!] = value }

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = apply { pending[key!!] = value }

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = apply { pending[key!!] = value }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = apply { pending[key!!] = value }

        override fun remove(key: String?): SharedPreferences.Editor = apply { removals += key!! }

        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (clear) map.clear()
            removals.forEach { map.remove(it) }
            map.putAll(pending)
        }
    }
}
