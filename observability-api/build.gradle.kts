plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM so the contract stays testable off-device and a future KMP move is cheap.
// Depends only on the OpenTelemetry *api* (not the SDK) — the same move platform-go makes by
// aliasing trace.Span rather than re-abstracting it.
dependencies {
    api(platform(libs.opentelemetry.bom))
    api(libs.opentelemetry.api)
    api(libs.opentelemetry.context)
    api(libs.opentelemetry.extension.kotlin)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
