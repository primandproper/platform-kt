package com.primandproper.platform.distributedlock.mock

import com.primandproper.platform.distributedlock.Lock
import com.primandproper.platform.distributedlock.Locker
import kotlin.time.Duration

/**
 * A configurable [Locker] test double, mirroring platform-go's moq-generated `mock.LockerMock`. Each
 * method delegates to a settable `...Func`; calling a method whose `Func` was left `null` throws
 * [IllegalStateException], the same "unmocked call surfaces immediately" behavior moq's generated
 * panic gives. Every call's arguments are recorded in the matching `...Calls` list, standing in for
 * moq's generated `XCalls()` accessors.
 *
 * Recording is guarded by a per-mock lock and the `...Calls` accessors hand back an immutable snapshot,
 * so a recorder on one thread can't throw [ConcurrentModificationException] against a reader on another.
 *
 * ```
 * val mock = LockerMock(acquireFunc = { key, ttl -> LockMock(keyFunc = { key }, ttlFunc = { ttl }) })
 * ```
 */
public class LockerMock(
    public var acquireFunc: (suspend (String, Duration) -> Lock)? = null,
    public var pingFunc: (suspend () -> Unit)? = null,
    public var closeFunc: (suspend () -> Unit)? = null,
) : Locker {
    private val lock = Any()
    private val _acquireCalls = mutableListOf<Pair<String, Duration>>()
    private var _pingCalls = 0
    private var _closeCalls = 0

    public val acquireCalls: List<Pair<String, Duration>> get() = synchronized(lock) { _acquireCalls.toList() }
    public val pingCalls: Int get() = synchronized(lock) { _pingCalls }
    public val closeCalls: Int get() = synchronized(lock) { _closeCalls }

    override suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock {
        synchronized(lock) { _acquireCalls += key to ttl }
        return requireFunc(acquireFunc, "acquireFunc").invoke(key, ttl)
    }

    override suspend fun ping() {
        synchronized(lock) { _pingCalls++ }
        requireFunc(pingFunc, "pingFunc").invoke()
    }

    override suspend fun close() {
        synchronized(lock) { _closeCalls++ }
        requireFunc(closeFunc, "closeFunc").invoke()
    }
}

/**
 * A configurable [Lock] test double, mirroring platform-go's moq-generated `mock.LockMock`. Follows
 * the same "null `Func` throws, calls are recorded" contract as [LockerMock]. [key] and [ttl] read
 * through `keyFunc`/`ttlFunc` (the analog of moq's `KeyFunc`/`TTLFunc`), so a test controls what a
 * handle reports.
 */
public class LockMock(
    public var keyFunc: (() -> String)? = null,
    public var ttlFunc: (() -> Duration)? = null,
    public var releaseFunc: (suspend () -> Unit)? = null,
    public var refreshFunc: (suspend (Duration) -> Unit)? = null,
) : Lock {
    private val lock = Any()
    private var _keyCalls = 0
    private var _ttlCalls = 0
    private var _releaseCalls = 0
    private val _refreshCalls = mutableListOf<Duration>()

    public val keyCalls: Int get() = synchronized(lock) { _keyCalls }
    public val ttlCalls: Int get() = synchronized(lock) { _ttlCalls }
    public val releaseCalls: Int get() = synchronized(lock) { _releaseCalls }
    public val refreshCalls: List<Duration> get() = synchronized(lock) { _refreshCalls.toList() }

    override val key: String
        get() {
            synchronized(lock) { _keyCalls++ }
            return requireFunc(keyFunc, "keyFunc").invoke()
        }

    override val ttl: Duration
        get() {
            synchronized(lock) { _ttlCalls++ }
            return requireFunc(ttlFunc, "ttlFunc").invoke()
        }

    override suspend fun release() {
        synchronized(lock) { _releaseCalls++ }
        requireFunc(releaseFunc, "releaseFunc").invoke()
    }

    override suspend fun refresh(ttl: Duration) {
        synchronized(lock) { _refreshCalls += ttl }
        requireFunc(refreshFunc, "refreshFunc").invoke(ttl)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
