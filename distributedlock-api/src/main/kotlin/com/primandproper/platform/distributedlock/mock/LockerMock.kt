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
 * ```
 * val mock = LockerMock(acquireFunc = { key, ttl -> LockMock(keyFunc = { key }, ttlFunc = { ttl }) })
 * ```
 */
public class LockerMock(
    public var acquireFunc: (suspend (String, Duration) -> Lock)? = null,
    public var pingFunc: (suspend () -> Unit)? = null,
    public var closeFunc: (suspend () -> Unit)? = null,
) : Locker {
    public val acquireCalls: MutableList<Pair<String, Duration>> = mutableListOf()
    public val pingCalls: MutableList<Unit> = mutableListOf()
    public val closeCalls: MutableList<Unit> = mutableListOf()

    override suspend fun acquire(
        key: String,
        ttl: Duration,
    ): Lock {
        acquireCalls += key to ttl
        return requireFunc(acquireFunc, "acquireFunc").invoke(key, ttl)
    }

    override suspend fun ping() {
        pingCalls += Unit
        requireFunc(pingFunc, "pingFunc").invoke()
    }

    override suspend fun close() {
        closeCalls += Unit
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
    public val keyCalls: MutableList<Unit> = mutableListOf()
    public val ttlCalls: MutableList<Unit> = mutableListOf()
    public val releaseCalls: MutableList<Unit> = mutableListOf()
    public val refreshCalls: MutableList<Duration> = mutableListOf()

    override val key: String
        get() {
            keyCalls += Unit
            return requireFunc(keyFunc, "keyFunc").invoke()
        }

    override val ttl: Duration
        get() {
            ttlCalls += Unit
            return requireFunc(ttlFunc, "ttlFunc").invoke()
        }

    override suspend fun release() {
        releaseCalls += Unit
        requireFunc(releaseFunc, "releaseFunc").invoke()
    }

    override suspend fun refresh(ttl: Duration) {
        refreshCalls += ttl
        requireFunc(refreshFunc, "refreshFunc").invoke(ttl)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
