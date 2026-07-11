package com.primandproper.platform.distributedlock

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import kotlin.time.Duration

/**
 * Acquires the lock named [key] with the supplied [ttl], runs [block] while holding it, and releases
 * the lock in a `finally` — the distributed-lock analog of [kotlinx.coroutines.sync.Mutex.withLock].
 * Prefer this over a hand-rolled `acquire`/`try`/`finally { release() }`: [Lock.release] throws
 * [LockNotHeldException] when the caller no longer owns the lock (TTL expiry, theft after expiry),
 * so a naive `finally { release() }` can replace [block]'s own result or exception with a spurious
 * [LockNotHeldException]. This helper swallows that release-on-expiry failure instead of letting it
 * mask [block]'s outcome.
 *
 * If the lock cannot be acquired the acquisition failure propagates and [block] never runs — see
 * [Locker.acquire] for the thrown types ([LockNotAcquiredException], [EmptyKeyException],
 * [InvalidTtlException]).
 *
 * @param logger optional logger; a swallowed [LockNotHeldException] from the release is recorded at
 *   error level (the lock was lost before [block] finished — usually the TTL was too short). Defaults
 *   to [NoopLogger], matching the rest of the module's optional-logger convention.
 * @return whatever [block] returns.
 */
public suspend fun <T> Locker.withLock(
    key: String,
    ttl: Duration,
    logger: Logger = NoopLogger,
    block: suspend () -> T,
): T {
    val lock = acquire(key, ttl)
    try {
        return block()
    } finally {
        try {
            lock.release()
        } catch (e: LockNotHeldException) {
            // The lock was already lost (TTL expiry or theft after expiry) by the time block finished.
            // Swallow it: propagating here would replace block's own result or exception with a
            // spurious LockNotHeldException. Only this expected release-on-expiry case is caught, so
            // any other failure (including cancellation) still surfaces.
            logger.error("releasing distributed lock '$key' after use (lock no longer held)", e)
        }
    }
}
