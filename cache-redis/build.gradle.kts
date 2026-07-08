plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Redis server backend (port of platform-go's `cache/redis` and the slot-computation core of
// `cache/redis/slots`). Depends on `:cache-api` for the `Cache`/`BatchCache` contract it implements
// and the in-memory backend the `provideCache` factory dispatches to.
//
// Lettuce is the Redis driver: its async command API returns `RedisFuture` (a `CompletionStage`),
// which `LettuceRedisClient` bridges to coroutines with `kotlinx.coroutines.future.await()` (part of
// kotlinx-coroutines-core since 1.6). A live Redis is NOT required to build or unit-test: the mapping
// logic is exercised against a fake `RedisClient`, and the Lettuce client connects lazily.
//
// New third-party coordinate is declared directly here (not in the version catalog) to keep parallel
// module work mergeable.
dependencies {
    api(project(":cache-api"))
    api(project(":observability-api"))
    api(project(":circuitbreaking"))
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
