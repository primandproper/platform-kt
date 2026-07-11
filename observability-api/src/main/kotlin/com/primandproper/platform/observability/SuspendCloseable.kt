package com.primandproper.platform.observability

/**
 * The platform-wide teardown contract for a resource whose `close()` genuinely does I/O — a network
 * flush, a WebSocket goodbye frame, a connection-pool drain. It is the `suspend` sibling of
 * [kotlin.AutoCloseable]: an interface for which `close()` is a `suspend fun`, so it may await its
 * transport without blocking a thread, and so callers can bracket it with the [use] extension below
 * exactly as they would an `AutoCloseable`.
 *
 * ## Why it lives in observability-api
 * There is no dedicated "core" module in platform-kt; `observability-api` is the de-facto shared
 * spine — a near-leaf that every service module already depends on (each opens an `Observer` span).
 * It already models a lifecycle here ([Operation.makeCurrent] hands back an `AutoCloseable` a caller
 * brackets), so the suspend analog is at home alongside it. Putting the type here means the I/O-backed
 * lifecycle interfaces (`SecretSource`, `EventReporter`, `HttpClient`, `AsyncNotifier`, messagequeue's
 * `Publisher`/`Consumer`/providers, `Locker`, `EventStream`) share ONE closer type without any of them
 * taking a new dependency they didn't already have.
 *
 * Genuinely synchronous, in-memory lifecycles (`RateLimiter`, `FeatureFlagManager`, `DatabaseClient`)
 * stay on [kotlin.AutoCloseable] — their `close()` does no I/O and need not suspend.
 */
public fun interface SuspendCloseable {
    /** Releases the resource, suspending until teardown completes. Implementations should be idempotent. */
    public suspend fun close()
}

/**
 * Executes [block] with this [SuspendCloseable], closing it in a `finally` afterwards — the `suspend`
 * mirror of [kotlin.AutoCloseable.use]. If [block] throws, the resource is still closed, and a
 * failure from [close] does not mask the original exception (it is attached as a suppressed exception).
 */
public suspend inline fun <T : SuspendCloseable, R> T.use(block: (T) -> R): R {
    var thrown: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        thrown = e
        throw e
    } finally {
        when (thrown) {
            null -> close()
            else ->
                try {
                    close()
                } catch (closeException: Throwable) {
                    thrown.addSuppressed(closeException)
                }
        }
    }
}
