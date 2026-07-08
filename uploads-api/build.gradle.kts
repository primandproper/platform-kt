plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the object-storage contract (`UploadManager` + the optional capability
// interfaces), its value types, the noop/mock doubles, the config + provider constants, the image
// validation helpers, and the two non-cloud backends — an in-memory `MemoryBucket` and a
// `FilesystemBucket` — driven through the single instrumented `Uploader`. Port of platform-go's
// `uploads`, `uploads/noop`, `uploads/mock`, `uploads/config`, `uploads/images`, and the
// provider-agnostic core of `uploads/objectstorage`.
//
// gocloud.dev's `blob.Bucket` is the seam platform-go instruments once and swaps backends behind; the
// Kotlin analog is the `Bucket` SPI here — `Uploader` wraps a `Bucket` and owns every span, so the S3
// backend (`:uploads-s3`) contributes only an `S3Bucket` and reuses this `Uploader` verbatim.
//
// Depends on observability-api because `Uploader` genuinely instruments every operation (an Observer
// span per method, the filename/length recorded on both pillars), mirroring the Go `Uploader`; the
// `Observer` type shows up in the public constructor, so the dependency is `api`. On `:errors` for the
// `ErrNilConfig`/`ErrUnknownProvider` sentinels (public, so `api`). Coroutines are `api`: every method
// suspends and `Lister.list` returns a `Flow`.
//
// No metrics / no circuit breaker: platform-go's `Uploader` records save/read/delete counters and a
// latency histogram through a metrics provider and wraps every call in a `circuitbreaking.CircuitBreaker`.
// There is no metrics pillar in platform-kt's observability-api, and `:circuitbreaking` is outside this
// port's dependency set, so both are documented `TODO(metrics)` / `TODO(circuitbreaking)` seams — the
// same descope `:cache-api` makes.
dependencies {
    api(project(":observability-api"))
    api(project(":errors"))
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
