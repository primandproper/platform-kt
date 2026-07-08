plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The integration/load-test harness (port of platform-go's `testutils` package and its
// `testutils/containers` + `testutils/containers/redistest` helpers). This module is test SUPPORT:
// its MAIN sources are the harness that OTHER modules pull in as a `testImplementation`, which is
// why they live under `src/main` (public, `explicitApi()`) rather than `src/test` — exactly like
// `:observability-testing`.
//
// `:retry` is `api`: `DefaultRetryConfig` returns a `RetryConfig` and `startWithRetry` runs a
// container builder under an `ExponentialBackoffPolicy`, both part of the public harness surface,
// mirroring how Go's `containers` package builds on `platform-go/v3/retry`. Coroutines are `api`
// too: `startWithRetry` (and every container `start`) suspends, standing in for the `context.Context`
// Go threads through `StartWithRetry`/`Try`.
//
// JUnit Jupiter is `api` because `skipIfNotRunning` gates container-backed tests via
// `Assumptions.assumeTrue` — the JUnit5 analog of Go's `tb.SkipNow()`; a test harness legitimately
// carries the test framework on its public surface.
//
// Testcontainers is the container runtime, declared as `api` (it is part of the harness surface: the
// `GenericContainer` a `start` hands back is what callers drive). Its new coordinate is declared
// DIRECTLY here, not in the version catalog, to keep parallel module work mergeable. A live Docker
// daemon is NOT required to build or unit-test: this module's own tests exercise only the pure logic
// (config building, JDBC/redis URL assembly, option merging, the retry wrapper against a fake
// container). The `start*` functions that actually stand up a `GenericContainer` are compile-surface
// only — gated behind `RUN_CONTAINER_TESTS`, exactly as the search/messagequeue backends leave their
// live-infra adapters uncovered.
dependencies {
    api(project(":retry"))
    api(libs.kotlinx.coroutines.core)
    api(libs.junit.jupiter)
    api("org.testcontainers:testcontainers:1.20.4")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
