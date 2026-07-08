/*
 * Postgres testcontainer setup, the JDBC-serving sibling of `RedisContainerConfig`. Go's
 * `testutils/containers` ships only `redistest`; this applies the same pattern (shared retry policy,
 * shared wait strategy, an options block) to Postgres so the database-backed suites get a
 * `jdbc:postgresql://…` URL from one place — the "expose its JDBC" surface the harness calls for.
 *
 * As with Redis, a plain `GenericContainer` on the postgres image stands in, since the single
 * `org.testcontainers:testcontainers` core coordinate this harness pins carries no `PostgreSQLContainer`
 * module.
 */
package com.primandproper.platform.testutils.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/** The postgres image [startPostgresContainer] launches when [PostgresContainerConfig.image] is left at its default. */
public const val DEFAULT_POSTGRES_IMAGE: String = "docker.io/postgres:16-bookworm"

/** The port postgres listens on inside the container. */
public const val POSTGRES_PORT: Int = 5432

/**
 * Configures a Postgres container. Built with the mutable `{ }` block idiom used across this port,
 * the [image] plus the database/credentials the image's `POSTGRES_*` env vars are seeded from.
 */
public class PostgresContainerConfig {
    /** Overrides [DEFAULT_POSTGRES_IMAGE]. */
    public var image: String = DEFAULT_POSTGRES_IMAGE

    /** The database created on first boot (`POSTGRES_DB`). */
    public var database: String = "testdb"

    /** The superuser role created on first boot (`POSTGRES_USER`). */
    public var username: String = "test"

    /** The superuser password (`POSTGRES_PASSWORD`). */
    public var password: String = "test"
}

/**
 * Assembles the `POSTGRES_*` environment the image seeds its initial database from. Split out so the
 * option-merging is unit-testable without Docker.
 */
internal fun postgresEnv(config: PostgresContainerConfig): Map<String, String> =
    mapOf(
        "POSTGRES_DB" to config.database,
        "POSTGRES_USER" to config.username,
        "POSTGRES_PASSWORD" to config.password,
    )

/**
 * Assembles a `jdbc:postgresql://host:port/database` URL — the DSN a JDBC driver dials. Pure string
 * assembly, so it is unit-testable without a running container; callers combine `container.host` and
 * `container.getMappedPort(POSTGRES_PORT)` with the configured database name.
 */
public fun postgresJdbcUrl(
    host: String,
    port: Int,
    database: String,
): String = "jdbc:postgresql://$host:$port/$database"

/**
 * Brings up a Postgres container under the shared retry policy ([startWithRetry]) and wait strategy,
 * returning the started [GenericContainer]. Build its JDBC URL with [postgresJdbcUrl] (`container.host`
 * + `container.getMappedPort(POSTGRES_PORT)` + [PostgresContainerConfig.database]).
 *
 * REQUIRES A LIVE DOCKER DAEMON — this is compile-surface only, not exercised by this module's unit
 * tests (which cover [postgresEnv]/[postgresJdbcUrl] and the retry wrapper); gate its callers behind
 * [skipIfNotRunning]. The returned container is [AutoCloseable]; callers own its shutdown.
 */
public suspend fun startPostgresContainer(configure: PostgresContainerConfig.() -> Unit = {}): GenericContainer<*> {
    val config = PostgresContainerConfig().apply(configure)

    // A concrete SELF satisfies Testcontainers' recursive `SELF extends GenericContainer<SELF>`
    // bound, so the builder methods return this type (not `Nothing`) and read as ordinary statements.
    class PostgresTestContainer(image: DockerImageName) : GenericContainer<PostgresTestContainer>(image)
    return startWithRetry {
        val container = PostgresTestContainer(DockerImageName.parse(config.image))
        container.withExposedPorts(POSTGRES_PORT)
        container.withEnv(postgresEnv(config))
        container.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2))
        container.start()
        container
    }
}
