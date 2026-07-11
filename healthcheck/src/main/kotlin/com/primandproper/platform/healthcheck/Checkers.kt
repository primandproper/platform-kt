package com.primandproper.platform.healthcheck

import com.primandproper.platform.errors.PlatformException
import com.primandproper.platform.errors.newError

/**
 * Thrown when a database is not ready to serve. Port of platform-go's `database.ErrDatabaseNotReady`,
 * raised by the database checker when the client reports it is not yet ready.
 *
 * In platform-go this type lives in the `database` package. It is declared here so the checker has a
 * canonical type to raise without depending on a database module; it should move once that module is
 * wired into this checker.
 */
public class DatabaseNotReadyException : PlatformException("database is not ready yet")

/**
 * The minimal readiness surface a database client exposes to the health system. Port of platform-go's
 * `DatabaseReadyChecker`. Declared here — rather than depending on a database module — so the checker
 * stays decoupled from any concrete client, exactly as in Go.
 */
public interface DatabaseReadyChecker {
    public suspend fun isReady(): Boolean
}

/**
 * Returns a [Checker] that reports [Status.DOWN][Status] when [client] is not ready. Port of
 * platform-go's `NewDatabaseChecker`. A `null` [client] fails the check with a "database client is
 * nil" error, matching Go's nil guard.
 */
public fun DatabaseChecker(
    name: String,
    client: DatabaseReadyChecker?,
): Checker = DatabaseCheckerImpl(name, client)

private class DatabaseCheckerImpl(
    override val name: String,
    private val client: DatabaseReadyChecker?,
) : Checker {
    override suspend fun check() {
        if (client == null) throw newError("database client is nil")
        if (!client.isReady()) throw DatabaseNotReadyException()
    }
}

/**
 * The minimal readiness surface a cache client exposes to the health system. Port of platform-go's
 * `CacheReadyChecker` — its own narrow interface, so the checker does not depend on `:cache-api`.
 */
public interface CacheReadyChecker {
    /** Verifies the cache is reachable, throwing if it is not. */
    public suspend fun ping()
}

/**
 * Returns a [Checker] that pings [client]. Port of platform-go's `NewCacheChecker`. A `null` [client]
 * fails the check with a "cache client is nil" error; otherwise the check fails iff [CacheReadyChecker.ping]
 * throws.
 */
public fun CacheChecker(
    name: String,
    client: CacheReadyChecker?,
): Checker = CacheCheckerImpl(name, client)

private class CacheCheckerImpl(
    override val name: String,
    private val client: CacheReadyChecker?,
) : Checker {
    override suspend fun check() {
        if (client == null) throw newError("cache client is nil")
        client.ping()
    }
}

/**
 * The minimal readiness surface a message-queue client exposes to the health system. Port of
 * platform-go's `MessageQueueReadyChecker`.
 */
public interface MessageQueueReadyChecker {
    /** Verifies the message queue is reachable, throwing if it is not. */
    public suspend fun ping()
}

/**
 * Returns a [Checker] that pings [client]. Port of platform-go's `NewMessageQueueChecker`. A `null`
 * [client] fails the check with a "message queue client is nil" error; otherwise the check fails iff
 * [MessageQueueReadyChecker.ping] throws.
 */
public fun MessageQueueChecker(
    name: String,
    client: MessageQueueReadyChecker?,
): Checker = MessageQueueCheckerImpl(name, client)

private class MessageQueueCheckerImpl(
    override val name: String,
    private val client: MessageQueueReadyChecker?,
) : Checker {
    override suspend fun check() {
        if (client == null) throw newError("message queue client is nil")
        client.ping()
    }
}
