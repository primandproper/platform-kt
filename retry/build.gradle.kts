plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM so the policy stays testable off-device and a future KMP move is cheap.
// Coroutine-native: retry loops suspend via `delay()` rather than blocking a thread, so
// kotlinx-coroutines-core is an `api` dependency (it appears in the public `Policy` signature).
dependencies {
    api(libs.kotlinx.coroutines.core)
    // An optional Logger appears in the public policy/flow signatures, so :observability-api is `api`.
    api(project(":observability-api"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
