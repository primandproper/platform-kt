/*
 * Single source of truth for the Redis testcontainer setup that the Redis-backed suites across the
 * repo would otherwise each duplicate — the port of platform-go's `testutils/containers/redistest`.
 * It owns the shared retry policy and wait strategy, so each caller only expresses what shape it
 * wants the node in.
 *
 * The Java Testcontainers core ships `GenericContainer` (there is no separate Redis module on the
 * single `org.testcontainers:testcontainers` coordinate this harness pins), so a plain
 * `GenericContainer` on the redis image stands in for Go's `rediscontainers.RedisContainer`.
 */
package com.primandproper.platform.testutils.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/** The redis image [startRedisContainer] launches when [RedisContainerConfig.image] is left at its default. */
public const val DEFAULT_REDIS_IMAGE: String = "docker.io/redis:7-bullseye"

/** The port redis-server listens on inside the container. */
public const val REDIS_PORT: Int = 6379

/**
 * Configures a Redis container. Built with the mutable `{ }` block idiom used across this port (see
 * `RetryConfig`), standing in for Go's variadic `Option` functions `WithImage`/`WithClusterEnabled`.
 */
public class RedisContainerConfig {
    /** Overrides [DEFAULT_REDIS_IMAGE]. */
    public var image: String = DEFAULT_REDIS_IMAGE

    /**
     * When true, passes `--cluster-enabled yes` to redis-server. The node still has no slots
     * assigned, but `CLUSTER` subcommands like `CLUSTER KEYSLOT` become available — useful for tests
     * that want Redis as a hash oracle without orchestrating a full multi-node cluster.
     */
    public var clusterEnabled: Boolean = false
}

/**
 * Assembles the container command for [config]: the full `redis-server` invocation with
 * `--cluster-enabled yes` when [RedisContainerConfig.clusterEnabled] is set, or an empty list to
 * leave the image's default CMD untouched. Split out so the option-merging is unit-testable without
 * Docker (the Java `withCommand` replaces CMD rather than appending, so the base command is spelled
 * out explicitly, unlike Go's `WithCmdArgs`).
 */
internal fun redisCommand(config: RedisContainerConfig): List<String> =
    if (config.clusterEnabled) {
        listOf("redis-server", "--cluster-enabled", "yes")
    } else {
        emptyList()
    }

/**
 * Assembles a `host:port` dial address — the shape most callers want, matching Go's `Address`, which
 * returns `ConnectionString` with the `redis://` scheme trimmed. Pure string assembly, so it is
 * unit-testable without a running container.
 */
public fun redisAddress(
    host: String,
    port: Int,
): String = "$host:$port"

/**
 * Trims the `redis://` scheme from a connection string, the transform Go's `Address` applies to
 * `ConnectionString`. Left as its own function so the URL logic is testable independent of a live
 * container.
 */
internal fun stripRedisScheme(connectionString: String): String = connectionString.removePrefix("redis://")

/**
 * Brings up a Redis container under the shared retry policy ([startWithRetry]) and wait strategy,
 * returning the started [GenericContainer] — the port of `redistest.Start`/`Try`. Read its dial
 * address with [redisAddress] (`container.host` + `container.getMappedPort(REDIS_PORT)`).
 *
 * REQUIRES A LIVE DOCKER DAEMON — this is compile-surface only. It is not exercised by this module's
 * unit tests (which cover [redisCommand]/[redisAddress]/[stripRedisScheme] and the retry wrapper);
 * gate its callers behind [skipIfNotRunning]. The returned container is [AutoCloseable]; callers own
 * its shutdown (e.g. `container.use { }` or an `@AfterEach`), standing in for Go's `tb.Cleanup`.
 */
public suspend fun startRedisContainer(configure: RedisContainerConfig.() -> Unit = {}): GenericContainer<*> {
    val config = RedisContainerConfig().apply(configure)

    // A concrete SELF satisfies Testcontainers' recursive `SELF extends GenericContainer<SELF>`
    // bound, so the builder methods return this type (not `Nothing`) and read as ordinary statements.
    class RedisTestContainer(image: DockerImageName) : GenericContainer<RedisTestContainer>(image)
    return startWithRetry {
        val container = RedisTestContainer(DockerImageName.parse(config.image))
        container.withExposedPorts(REDIS_PORT)
        container.waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
        val command = redisCommand(config)
        if (command.isNotEmpty()) {
            container.withCommand(*command.toTypedArray())
        }
        container.start()
        container
    }
}
