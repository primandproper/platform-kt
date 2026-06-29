plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. opentelemetry-sdk is plain Java and runs fine on Android (with core library
// desugaring enabled in the consuming app), so this module needs no Android plugin.
dependencies {
    api(project(":observability-api"))
    api(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)

    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
