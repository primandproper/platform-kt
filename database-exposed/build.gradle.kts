plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Postgres database backend, implemented with JetBrains Exposed over JDBC (port of platform-go's
// `database/postgres` and `database/postgres/tableaccess`). Depends on `:database-api` for the
// `DatabaseClient`/`Manager`/`ClientConfig` contracts it fulfils and on `:observability-api` for the
// `Observer` it instruments every connection/ping/rollback with, mirroring the Go client.
//
// Exposed and the Postgres JDBC driver are declared directly here (not in the version catalog) to
// keep parallel module work mergeable, exactly as `:cache-redis` declares Lettuce. A live Postgres is
// NOT required to build or unit-test: `PGSimpleDataSource` connects lazily, and the tests drive an
// in-memory H2 database (a testImplementation coordinate), the same way `:cache-redis` tests a fake
// client.
//
// Seams left as documented TODOs: the MySQL and SQLite backends (`TODO(mysql)` / `TODO(sqlite)`) and a
// reactive R2DBC path (`TODO(r2dbc)`) — named in `ProvideDatabase`, not dropped.
dependencies {
    api(project(":database-api"))
    api(project(":observability-api"))

    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation(libs.kotlinx.coroutines.core)

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
