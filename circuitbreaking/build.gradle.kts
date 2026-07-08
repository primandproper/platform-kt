plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM so the breaker stays testable off-device and a future KMP move is cheap.
// Coroutine-native: `execute` suspends and the reset timer is read on access, so
// kotlinx-coroutines-core is an `api` dependency (StateFlow and the suspend signature are public).
// Owns the canonical `ErrCircuitBroken` sentinel (currently stubbed in :errors) and logs state
// transitions through :observability-api — the metrics pillar is descoped (see TODO(metrics)).
dependencies {
    api(project(":observability-api"))
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
