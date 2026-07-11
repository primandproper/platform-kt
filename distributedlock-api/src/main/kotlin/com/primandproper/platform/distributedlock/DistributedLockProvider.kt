package com.primandproper.platform.distributedlock

/**
 * The supported distributed-lock providers. Port of platform-go's `distributedlockcfg` provider
 * constants (`RedisProvider` / `PostgresProvider` / `MemoryProvider` / `NoopProvider`). [value] is
 * the wire/string form validated against configuration.
 *
 * The Redis and Postgres connection settings live with their backends (`RedisLockConfig` in
 * `:distributedlock-redis`, `PostgresLockConfig` in `:distributedlock-postgres`), so this API module
 * stays pure-JVM and free of any backend dependency — mirroring how Go's `distributedlock/config`
 * sits above `distributedlock/memory`, `.../noop`, `.../redis`, and `.../postgres`. Each backend
 * exposes its own constructor (`RedisLocker(...)`, `PostgresLocker(...)`, [MemoryLocker], [NoopLocker]);
 * a consuming app dispatches on the selected [DistributedLockProvider], the analog of Go's
 * `ProvideLocker` switch.
 */
public enum class DistributedLockProvider(
    public val value: String,
) {
    REDIS("redis"),
    POSTGRES("postgres"),
    MEMORY("memory"),
    NOOP("noop"),
    ;

    public companion object {
        /**
         * Resolves a provider from its string [value] (trimmed, case-insensitive), throwing
         * [IllegalArgumentException] if it names no known provider.
         *
         * This deliberately does NOT fall back to [NOOP] on an unrecognized string: a silent fallback
         * turns a config typo (`"redsi"`, `"postgress"`) into an always-grant no-op locker — distributed
         * locking quietly disabled in production. Opting out of real locking must be explicit via the
         * [NOOP] (`"noop"`) provider. Mirrors the loud-failure resolution used elsewhere in platform-kt
         * (e.g. `PaymentManager` throwing on an unknown provider) rather than Go's
         * lenient `default:` branch.
         */
        public fun fromValue(value: String): DistributedLockProvider {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized }
                ?: throw IllegalArgumentException(
                    "unknown distributed-lock provider: \"$value\" (valid: ${entries.joinToString(", ") { it.value }})",
                )
        }
    }
}
