package com.primandproper.platform.ratelimiting

/**
 * Limits the rate of operations per key using a token-bucket algorithm. Port of platform-go's
 * `ratelimiting.RateLimiter`.
 *
 * Go's `Allow(ctx, key) (bool, error)` threads a `context.Context` and returns a `(bool, error)`
 * pair; the idiomatic Kotlin shape is a `suspend fun` that returns the boolean and signals a backend
 * failure by throwing (a server backend's [allow] can throw when the store is unreachable). A `true`
 * result means the request is within the limit and should proceed; `false` means it is rate limited.
 *
 * [RateLimiter] extends [AutoCloseable] so it slots into `use { }` and DI lifecycles; [close] is the
 * analog of Go's `Close() error`, releasing any per-key state or backing connection.
 */
public interface RateLimiter : AutoCloseable {
    /** Returns `true` if a request for [key] is within the configured limit, `false` if it is throttled. */
    public suspend fun allow(key: String): Boolean

    /** Releases resources held by the limiter. Analog of Go's `Close() error`. */
    override fun close()
}
