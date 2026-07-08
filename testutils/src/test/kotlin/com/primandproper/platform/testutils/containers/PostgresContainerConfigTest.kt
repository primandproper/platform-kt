package com.primandproper.platform.testutils.containers

import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises the pure (no-Docker) JDBC/DSN and option-merging logic of the Postgres helper. */
class PostgresContainerConfigTest {
    @Test
    fun defaultConfigUsesTheDefaultImageAndCredentials() {
        val cfg = PostgresContainerConfig()
        assertEquals(DEFAULT_POSTGRES_IMAGE, cfg.image)
        assertEquals("testdb", cfg.database)
        assertEquals("test", cfg.username)
        assertEquals("test", cfg.password)
    }

    @Test
    fun postgresEnvMergesConfiguredValues() {
        val env =
            postgresEnv(
                PostgresContainerConfig().apply {
                    database = "orders"
                    username = "svc"
                    password = "hunter2"
                },
            )
        assertEquals(
            mapOf(
                "POSTGRES_DB" to "orders",
                "POSTGRES_USER" to "svc",
                "POSTGRES_PASSWORD" to "hunter2",
            ),
            env,
        )
    }

    @Test
    fun postgresJdbcUrlAssemblesADialableDsn() {
        assertEquals(
            "jdbc:postgresql://localhost:5432/testdb",
            postgresJdbcUrl("localhost", POSTGRES_PORT, "testdb"),
        )
        assertEquals(
            "jdbc:postgresql://127.0.0.1:49154/orders",
            postgresJdbcUrl("127.0.0.1", 49154, "orders"),
        )
    }
}
