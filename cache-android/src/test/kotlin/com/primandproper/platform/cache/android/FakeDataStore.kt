package com.primandproper.platform.cache.android

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An in-memory [DataStore] of [Preferences], so [DataStoreCache] can be unit-tested on the JVM with no
 * device, file I/O, or Robolectric. Structurally identical to the real Preferences DataStore from the
 * cache's point of view: it reads `data` and mutates via `updateData` (through the `edit` extension).
 */
class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> get() = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}
