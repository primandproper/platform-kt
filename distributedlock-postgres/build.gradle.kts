plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Postgres server backend (port of platform-go's `distributedlock/postgres`). Implements the
// `:distributedlock-api` Locker/Lock contract against PostgreSQL session-scoped advisory locks
// (`pg_try_advisory_lock` / `pg_advisory_unlock`). Each held lock pins a dedicated connection for its
// whole lifetime, because the advisory lock lives on that session; on a failed unlock the connection
// is force-discarded so the lock can't leak back into the pool.
//
// IMPORTANT — TTL semantics: PostgreSQL advisory locks have no native TTL. The TTL argument is
// ADVISORY ONLY: the lock is held until Release is called or the dedicated session is closed. The
// backend tracks expiry client-side (via an injectable clock) purely to honor the Lock contract, and
// Refresh is a liveness probe (`SELECT 1`), not a server-side extension.
//
// `:circuitbreaking` is `api` (the breaker is on the constructor surface); Go's
// `CannotProceed/Succeeded/Failed` trio is folded into the module's `execute { }`.
//
// A live Postgres is NOT required to build or unit-test. Advisory locks are Postgres-specific, so H2
// cannot exercise them; instead the key-hashing, SQL-building, and acquire/release/refresh LOGIC is
// tested against a fake `AdvisoryLockClient`, mirroring how `:cache-redis` fakes its client. The
// postgres JDBC driver coordinate is declared directly here (not in the version catalog), matching
// the parallel-module convention.
//
// TODO(metrics): Go records acquire/release/refresh/contend/error counters and a latency histogram
// through a metrics provider, absent from observability-api — left as a documented seam.
dependencies {
    api(project(":distributedlock-api"))
    api(project(":observability-api"))
    api(project(":circuitbreaking"))
    implementation(project(":identifiers"))
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
