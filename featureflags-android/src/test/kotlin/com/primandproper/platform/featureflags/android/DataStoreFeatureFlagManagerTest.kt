package com.primandproper.platform.featureflags.android

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FlagAttributes
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the DataStore-backed Android backend off-device: the DataStore is created over a temp
 * file (no Context), so the same bool/string/int/float/object matrix and default-on-miss behavior
 * from the platform-go suite runs in a plain JVM unit test.
 */
class DataStoreFeatureFlagManagerTest {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tempFile: File = File.createTempFile("ff-test", ".preferences_pb").also { it.delete() }
    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { tempFile }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        tempFile.delete()
    }

    private fun evalCtx(targetingKey: String = "user123") = EvaluationContext(targetingKey = targetingKey)

    @Test
    fun `canUseFeature returns stored value and observes fields`() =
        runBlocking {
            store.edit { it[booleanPreferencesKey("bool-flag")] = true }
            val observer = RecordingObserver()
            val mgr = DataStoreFeatureFlagManager(store, observer)

            assertTrue(mgr.canUseFeature("bool-flag", evalCtx()))
            observer.assertObservedOperationWithValues(
                Keys.USER_ID to "user123",
                FlagAttributes.FEATURE to "bool-flag",
                FlagAttributes.FLAG_VALUE to true,
            )
        }

    @Test
    fun `canUseFeature returns false when flag missing`() =
        runBlocking {
            assertFalse(DataStoreFeatureFlagManager(store).canUseFeature("nonexistent", evalCtx()))
        }

    @Test
    fun `getStringValue returns stored value or default`() =
        runBlocking {
            store.edit { it[stringPreferencesKey("string-flag")] = "hello-world" }
            val mgr = DataStoreFeatureFlagManager(store)
            assertEquals("hello-world", mgr.getStringValue("string-flag", "fallback", evalCtx()))
            assertEquals("fallback", mgr.getStringValue("nonexistent", "fallback", evalCtx()))
        }

    @Test
    fun `getInt64Value returns stored value or default`() =
        runBlocking {
            store.edit { it[longPreferencesKey("int-flag")] = 42L }
            val mgr = DataStoreFeatureFlagManager(store)
            assertEquals(42L, mgr.getInt64Value("int-flag", 7L, evalCtx()))
            assertEquals(7L, mgr.getInt64Value("nonexistent", 7L, evalCtx()))
        }

    @Test
    fun `getFloat64Value returns stored value or default`() =
        runBlocking {
            store.edit { it[doublePreferencesKey("float-flag")] = 3.14 }
            val mgr = DataStoreFeatureFlagManager(store)
            assertEquals(3.14, mgr.getFloat64Value("float-flag", 1.0, evalCtx()), 1e-9)
            assertEquals(1.0, mgr.getFloat64Value("nonexistent", 1.0, evalCtx()), 1e-9)
        }

    @Test
    fun `getObjectValue returns stored json string or default`() =
        runBlocking {
            store.edit { it[stringPreferencesKey("object-flag")] = """{"key":"value"}""" }
            val mgr = DataStoreFeatureFlagManager(store)
            assertEquals("""{"key":"value"}""", mgr.getObjectValue("object-flag", null, evalCtx()))
            val def = mapOf("k" to "v")
            assertEquals(def, mgr.getObjectValue("nonexistent", def, evalCtx()))
        }
}
