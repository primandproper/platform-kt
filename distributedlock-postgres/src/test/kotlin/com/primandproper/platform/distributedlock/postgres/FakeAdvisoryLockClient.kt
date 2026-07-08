package com.primandproper.platform.distributedlock.postgres

import kotlin.time.Duration

/**
 * An in-memory [AdvisoryLockClient] fake, standing in for a live Postgres so [PostgresLocker]'s
 * connection-pinning logic can be unit-tested. Advisory locks are Postgres-specific and cannot run on
 * H2, so — as the task requires and mirroring how `:cache-redis` fakes its client — the SQL-building,
 * key-hashing, and acquire/release/refresh LOGIC is exercised against this fake, not real infra.
 *
 * Each command kind has a configurable result or error; [reserve] can be scripted to throw a
 * [PoolSaturatedException] (the saturated-pool contention path) or any other error (the reservation
 * failure path). Handed-out connections are recorded so a test can assert they were released or
 * force-discarded.
 */
class FakeAdvisoryLockClient(
    var tryLockResult: Boolean = true,
    var unlockResult: Boolean = true,
    var alive: Boolean = true,
    var reserveError: Throwable? = null,
    var tryLockError: Throwable? = null,
    var unlockError: Throwable? = null,
    var pingError: Throwable? = null,
) : AdvisoryLockClient {
    val connections: MutableList<FakeAdvisoryLockConnection> = mutableListOf()
    var reserveCalls: Int = 0
    var pingCalls: Int = 0

    override suspend fun reserve(waitBudget: Duration): AdvisoryLockConnection {
        reserveCalls++
        reserveError?.let { throw it }
        return FakeAdvisoryLockConnection(this).also { connections += it }
    }

    override suspend fun ping() {
        pingCalls++
        pingError?.let { throw it }
    }

    override suspend fun close() {
    }
}

/** A fake dedicated session paired with [FakeAdvisoryLockClient]. */
class FakeAdvisoryLockConnection(
    private val client: FakeAdvisoryLockClient,
) : AdvisoryLockConnection {
    var tryLockCalls: Int = 0
    var unlockCalls: Int = 0
    var aliveCalls: Int = 0
    var released: Boolean = false
    var discarded: Boolean = false

    override suspend fun tryAdvisoryLock(lockId: Long): Boolean {
        tryLockCalls++
        client.tryLockError?.let { throw it }
        return client.tryLockResult
    }

    override suspend fun advisoryUnlock(lockId: Long): Boolean {
        unlockCalls++
        client.unlockError?.let { throw it }
        return client.unlockResult
    }

    override suspend fun isAlive(): Boolean {
        aliveCalls++
        return client.alive
    }

    override suspend fun release() {
        released = true
    }

    override suspend fun discard() {
        discarded = true
    }
}
