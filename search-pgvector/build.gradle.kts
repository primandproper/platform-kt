plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The pgvector vector-search backend (port of platform-go's `search/vector/pgvector`). Depends on
// `:search-api` for the `Index<T>` contract it implements and the noop the `provideVectorIndex`
// factory falls back to, and on `:observability-api` because every operation opens an Observer span
// (the type shows up in the public constructor).
//
// A live Postgres is NOT required to build or unit-test. pgvector's SQL is Postgres-specific (the
// `vector` type, `<=>`/`<#>`/`<->` operators, hnsw indexes, `CREATE EXTENSION vector`), so the
// SQL-building and row-mapping logic is exercised against a fake `PgvectorExecutor` that records the
// statements it received and returns canned rows — mirroring how `:cache-redis` tests against a fake
// Redis client. The generic JDBC adapter (`JdbcPgvectorExecutor`) is additionally proven end-to-end
// against in-memory H2 for the provider-agnostic transaction/exec/query wiring.
//
// New third-party coordinates are declared directly here (not in the version catalog) to keep
// parallel module work mergeable: the Postgres JDBC driver for production, H2 for the adapter tests.
dependencies {
    api(project(":search-api"))
    api(project(":observability-api"))
    implementation(project(":errors"))
    implementation("org.postgresql:postgresql:42.7.4")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
    testImplementation("com.h2database:h2:2.3.232")
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
