plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the relational-database abstraction (port of platform-go's `database`,
// `database/config`, `database/filtering`, and `database/mock`). Holds the querier/`Client` contract,
// the provider-aware config, query-filter/pagination helpers, the null-value converters, the
// access-manager and migration abstractions, and the moq-style test doubles.
//
// Depends on:
//   - :errors for the platform sentinels (`ErrDatabaseNotReady`, `ErrUserAlreadyExists`), mirroring
//     platform-go declaring them with `platformerrors.New`.
//   - :observability-api because `QueryFilter.attachToLogger` takes and returns a `Logger`, exactly
//     as platform-go's `filtering.QueryFilter.AttachToLogger` does — so the type is `api`.
//   - kotlinx-coroutines as `api`: every context-threading method (the `Manager`, the querier, the
//     `Migrator`) suspends instead of taking a `context.Context`, so coroutine types are part of the
//     public surface, matching how :cache-api carries them.
//
// The JVM analog of Go's `*sql.DB` is JDBC's `javax.sql.DataSource` (a JDK type), so the pure-JVM
// `DatabaseClient` contract can name it without pulling in any driver — the Exposed/Postgres
// implementation lives in :database-exposed.
//
// No metrics pillar: platform-go's config gates `db.sql.*` metrics behind a metrics provider. There
// is no metrics pillar in platform-kt's observability-api yet, so that is a documented `TODO(metrics)`
// seam, the same descope :cache-api and :circuitbreaking make.
dependencies {
    api(project(":errors"))
    api(project(":observability-api"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
