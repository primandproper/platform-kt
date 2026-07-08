plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The AWS S3 server backend (port of the S3 branch of platform-go's `objectstorage.selectBucket`,
// which opens an `s3blob` bucket over the AWS SDK). Depends on `:uploads-api` for the `Bucket` SPI it
// implements and the shared `Uploader`/`StorageConfig`/prefixing it reuses — the S3 backend contributes
// only an `S3Bucket` and wraps it in the same instrumented `Uploader`, exactly as gocloud instruments
// one `Uploader` over whichever bucket it opened.
//
// The AWS SDK v2 async client returns `CompletableFuture`, bridged to coroutines with
// `kotlinx.coroutines.future.await()` (in kotlinx-coroutines-core since 1.6) — the same pattern
// `:cache-redis` uses for Lettuce. A live bucket / AWS credentials are NOT required to build or
// unit-test: `S3Bucket` is exercised against a narrow fake `S3Client` (the analog of cache-redis's
// `FakeRedisClient`), and the real `AwsS3Client` builds its `S3AsyncClient` without connecting.
//
// The new AWS coordinate is declared directly here (not in the version catalog) to keep parallel module
// work mergeable.
dependencies {
    api(project(":uploads-api"))
    api(project(":observability-api"))
    implementation("software.amazon.awssdk:s3:2.29.20")
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
