plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the Locker/Lock contract, its error sentinels, the provider enum + config, the
// noop/mock doubles, and the in-memory backend (port of platform-go's `distributedlock`,
// `distributedlock/noop`, `distributedlock/mock`, and `distributedlock/memory`).
//
// Coroutines are `api`: every lock method suspends (Go threads a `context.Context`; Kotlin suspends
// instead), so `kotlinx.coroutines` types are part of the public surface.
//
// `:errors` is `api` because the exported sentinels (`ErrLockNotAcquired`, `ErrLockNotHeld`, …) are
// `PlatformException` values callers catch / `isError(...)` against, exactly like platform-go's
// `errors.Is(err, distributedlock.ErrLockNotAcquired)`.
//
// The in-memory backend genuinely instruments its Acquire path (an `Observer` span, the key/ttl on
// both pillars), mirroring platform-go's `memory.locker`, so `:observability-api` is `api` (the
// `Observer` type appears in the internal constructor, matching how `:cache-api` carries it).
//
// `:identifiers` is `implementation`: the in-memory backend mints an opaque ownership token per lock
// (Go's `identifiers.New()`), which never appears on the public surface.
//
// No metrics: platform-go's backends record acquire/release/refresh/contend counters and a latency
// histogram through a metrics provider. There is no metrics pillar in platform-kt's
// observability-api yet, so those are left as a documented `TODO(metrics)` seam, exactly as
// `:cache-api` and `:circuitbreaking` do.
dependencies {
    api(project(":observability-api"))
    api(project(":errors"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":identifiers"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
