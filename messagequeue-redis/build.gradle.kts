plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Redis pub/sub backend (port of platform-go's `messagequeue/redis`). Depends on
// `:messagequeue-api` for the Publisher/Consumer contract it implements and the noop providers the
// selection factory falls back to; on `:observability-api` because every publish/consume opens an
// Observer span (the `Observer` type shows up in the public factory signatures, so the dependency is
// `api`); and on `:circuitbreaking` to wrap each publish and each per-message consume under a
// CircuitBreaker, mirroring how `:analytics-segment` drives the coroutine-native breaker around its
// delivery. platform-go's messagequeue does not itself wrap, but the breaker is the resilience seam
// this port reattaches at the same publish/consume boundary; it is part of the public factory surface,
// so `:circuitbreaking` is `api`.
//
// Lettuce is the Redis driver: pub/sub subscribe delivers messages through a `RedisPubSubListener`
// bridged to a cold `Flow` (a `callbackFlow`), and each async command's `RedisFuture` is awaited via
// `kotlinx.coroutines.future.await`. A live Redis is NOT required to build or unit-test: the
// publish/consume mapping is exercised against a fake `RedisPubSubClient`, and the Lettuce adapter
// connects lazily. New third-party coordinate is declared directly here, not in the version catalog.
dependencies {
    api(project(":messagequeue-api"))
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
