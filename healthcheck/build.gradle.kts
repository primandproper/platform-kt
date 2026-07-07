plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the component health registry and the ready-checkers that feed it (port of
// platform-go's `healthcheck` package — `healthcheck.go` + `checkers.go`).
//
// Coroutines are `api`: every check runs under a deadline and each `Checker.check()` / `Registry`
// method suspends (Go threads a `context.Context`; Kotlin suspends and cancels structurally), so the
// `kotlinx.coroutines` types are part of the public surface.
//
// `:errors` is `api`: the checkers raise the platform error type on a nil client, and this module owns
// the `ErrDatabaseNotReady` sentinel (a `PlatformException`) that appears on the public surface. In
// platform-go that sentinel lives in the `database` package (`database.ErrDatabaseNotReady`); until
// `:database-api` is ported it is kept here, mirroring how `:circuitbreaking` temporarily owns
// `ErrCircuitBroken`.
//
// No observability dependency: unlike `:cache-api`, platform-go's `healthcheck` package does not
// instrument — it opens no Observer span and records nothing — so a faithful port carries none. The
// probe is meant to be exposed through an actuator-style endpoint; that HTTP wiring is the server
// module's job (see the TODO note on `Registry`), not this one's.
dependencies {
    api(project(":errors"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
