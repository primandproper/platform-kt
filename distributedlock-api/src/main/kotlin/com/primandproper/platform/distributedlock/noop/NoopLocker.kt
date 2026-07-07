package com.primandproper.platform.distributedlock.noop

import com.primandproper.platform.distributedlock.Lock
import com.primandproper.platform.distributedlock.Locker
import kotlin.time.Duration

/**
 * A no-op [Locker]: [acquire] always succeeds, [Lock.release] and [Lock.refresh] are no-ops, [ping]
 * succeeds. Port of platform-go's `distributedlock/noop.locker`.
 *
 * Use this when distributed locking is not needed in a given deployment (single replica, dev
 * environments), or as the safe fallback for an unknown/empty provider — the analog of Go's
 * `ProvideLocker` `default:` branch.
 */
public class NoopLocker : Locker {
    override suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock = NoopLock(key, ttl)

    override suspend fun ping() {
    }

    override suspend fun close() {
    }

    // A trivial Lock paired with the noop locker. Refresh updates the reported TTL but does no work.
    private class NoopLock(
        override val key: String,
        ttl: Duration,
    ) : Lock {
        private var currentTtl: Duration = ttl
        override val ttl: Duration get() = currentTtl

        override suspend fun release() {
        }

        override suspend fun refresh(ttl: Duration) {
            currentTtl = ttl
        }
    }
}
