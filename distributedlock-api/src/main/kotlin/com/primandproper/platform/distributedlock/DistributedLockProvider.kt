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
         * Resolves a provider from its string [value] (trimmed, case-insensitive), or [NOOP] if it
         * names no known provider — mirroring Go's `ProvideLocker` falling back to the noop locker for
         * an unknown or empty provider (`strings.TrimSpace(strings.ToLower(cfg.Provider))` then the
         * `default:` branch).
         */
        public fun fromValue(value: String): DistributedLockProvider {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.value == normalized } ?: NOOP
        }
    }
}
