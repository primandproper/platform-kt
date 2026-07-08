plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The distributed Redis rate-limiter backend (port of platform-go's `ratelimiting/redis`). Depends on
// `:ratelimiting-api` for the `RateLimiter` contract it implements and the noop/in-memory backends the
// `provideRateLimiter` factory dispatches to. Depends on `:identifiers` for the unique ZADD member
// (Go uses `identifiers.New()`), and on `:observability-api` for the per-`allow` Observer span.
//
// Lettuce is the Redis driver: its async command API returns `RedisFuture` (a `CompletionStage`),
// which `LettuceRedisClient` bridges to coroutines with `kotlinx.coroutines.future.await()`. A live
// Redis is NOT required to build or unit-test: the sliding-window mapping is exercised against a fake
// `RedisClient`, and the Lettuce client connects lazily.
//
// New third-party coordinate is declared directly here (not in the version catalog) to keep parallel
// module work mergeable — same Lettuce version as `:cache-redis`.
dependencies {
    api(project(":ratelimiting-api"))
    api(project(":observability-api"))
    implementation(project(":identifiers"))
    implementation("io.lettuce:lettuce-core:6.5.1.RELEASE")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
