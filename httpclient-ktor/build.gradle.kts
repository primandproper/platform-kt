plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. The server-side backend, over the Ktor client. Ktor is suspend-native, so calls
// run on the coroutine that issued them — the OpenTelemetry Ktor instrumentation therefore sees the
// observability span as the current OTel Context and parents its request span underneath it, the
// same tie-in the OkHttp backend gets. CIO is the default engine (pure JVM, no extra native deps).
dependencies {
    api(project(":httpclient-api"))
    api(platform(libs.opentelemetry.bom))
    implementation(platform(libs.opentelemetry.instrumentation.bom.alpha))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.opentelemetry.ktor)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.ktor.client.mock)
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
