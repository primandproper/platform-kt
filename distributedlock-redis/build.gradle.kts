plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Redis server backend (port of platform-go's `distributedlock/redis`). Implements the
// `:distributedlock-api` Locker/Lock contract with a SET NX PX acquire and a compare-and-delete /
// compare-and-pexpire Lua unlock, so a lock is only ever released or refreshed by its true owner.
//
// `:circuitbreaking` is `api`: `NewRedisLocker`/`RedisLocker(...)` takes a `CircuitBreaker` on its
// public surface, mirroring platform-go's constructor. Go drives the breaker with the primitive
// `CannotProceed()`/`Succeeded()`/`Failed()` trio; this port folds that into the module's single
// `execute { }` (a thrown backend error counts a failure, a normal return a success, an open breaker
// short-circuits with `ErrCircuitBroken`) — the same fold `:circuitbreaking` makes over Go's quartet.
//
// Lettuce is the Redis driver: its async command API returns `RedisFuture` (a `CompletionStage`),
// which `LettuceRedisLockClient` bridges to coroutines with `kotlinx.coroutines.future.await()`. A
// live Redis is NOT required to build or unit-test: the lock logic is exercised against a fake
// `RedisLockClient`, and the Lettuce client connects lazily. The new third-party coordinate is
// declared directly here (not in the version catalog) to keep parallel module work mergeable.
//
// TODO(cluster): platform-go's `buildRedisClient` also builds a cluster client for multi-address
// configs. A single-node `LettuceRedisLockClient` is the primary backend here; a cluster adapter is
// a documented seam. TODO(metrics): Go records acquire/release/refresh/contend/error counters and a
// latency histogram through a metrics provider, absent from observability-api — left as a seam.
dependencies {
    api(project(":distributedlock-api"))
    api(project(":observability-api"))
    api(project(":circuitbreaking"))
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
