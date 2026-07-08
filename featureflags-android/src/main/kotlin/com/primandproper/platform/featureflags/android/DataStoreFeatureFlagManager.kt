package com.primandproper.platform.featureflags.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FeatureFlagManager
import com.primandproper.platform.featureflags.FlagAttributes
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.noopObserver
import com.primandproper.platform.observability.span
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val SERVICE_NAME = "datastore_feature_flag_manager"

/** The default DataStore file name backing [dataStoreFeatureFlagManager]. */
public const val FEATURE_FLAGS_DATASTORE_NAME: String = "feature_flags"

private val Context.featureFlagDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FEATURE_FLAGS_DATASTORE_NAME,
)

/**
 * A local, persistable [FeatureFlagManager] backed by Jetpack DataStore Preferences. Each flag is
 * keyed by name and typed per accessor (boolean/string/long/double; object flags are stored as their
 * JSON string). A missing flag yields the caller-supplied default. Every evaluation opens an
 * observability span and records the subject, feature key, default, and resolved value — the same
 * instrumentation the server backends emit.
 *
 * Seed or update flags by editing the underlying [store] (`store.edit { it[booleanPreferencesKey(
 * "flag")] = true }`) — e.g. from a config sync job.
 *
 * TODO(launchdarkly-android): a LaunchDarkly-backed Android manager (wrapping
 * `com.launchdarkly:launchdarkly-android-client-sdk`) is a documented seam. It needs an Application
 * context and network initialization, so it is impractical to exercise in an off-device unit build;
 * this DataStore store is the working local backend in the meantime.
 */
public class DataStoreFeatureFlagManager(
    public val store: DataStore<Preferences>,
    observer: Observer? = null,
) : FeatureFlagManager {
    private val o11y: Observer = observer ?: noopObserver(SERVICE_NAME)

    override suspend fun canUseFeature(
        feature: String,
        evalCtx: EvaluationContext,
    ): Boolean =
        o11y.span("canUseFeature") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = store.data.map { it[booleanPreferencesKey(feature)] }.first() ?: false
            set(FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getStringValue(
        feature: String,
        defaultValue: String,
        evalCtx: EvaluationContext,
    ): String =
        o11y.span("getStringValue") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = store.data.map { it[stringPreferencesKey(feature)] }.first() ?: defaultValue
            set(FlagAttributes.FLAG_DEFAULT to defaultValue, FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getInt64Value(
        feature: String,
        defaultValue: Long,
        evalCtx: EvaluationContext,
    ): Long =
        o11y.span("getInt64Value") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = store.data.map { it[longPreferencesKey(feature)] }.first() ?: defaultValue
            set(FlagAttributes.FLAG_DEFAULT to defaultValue, FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getFloat64Value(
        feature: String,
        defaultValue: Double,
        evalCtx: EvaluationContext,
    ): Double =
        o11y.span("getFloat64Value") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val result = store.data.map { it[doublePreferencesKey(feature)] }.first() ?: defaultValue
            set(FlagAttributes.FLAG_DEFAULT to defaultValue, FlagAttributes.FLAG_VALUE to result)
            result
        }

    override suspend fun getObjectValue(
        feature: String,
        defaultValue: Any?,
        evalCtx: EvaluationContext,
    ): Any? =
        o11y.span("getObjectValue") {
            set(Keys.USER_ID to evalCtx.targetingKey, FlagAttributes.FEATURE to feature)
            val stored = store.data.map { it[stringPreferencesKey(feature)] }.first()
            stored ?: defaultValue
        }

    override fun close() {
        // The DataStore's lifecycle is owned by the caller (the Application scope), so there is
        // nothing to release per manager instance.
    }
}

/**
 * Builds a [DataStoreFeatureFlagManager] over the application-scoped feature-flag DataStore. This is
 * the production entry point; unit tests construct [DataStoreFeatureFlagManager] directly over a
 * temp-file DataStore.
 */
public fun dataStoreFeatureFlagManager(
    context: Context,
    observer: Observer? = null,
): DataStoreFeatureFlagManager = DataStoreFeatureFlagManager(context.applicationContext.featureFlagDataStore, observer)
