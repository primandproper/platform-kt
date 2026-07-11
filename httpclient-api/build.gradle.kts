plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM so the HTTP contract stays testable off-device and a future KMP move is cheap.
// This is the keystone every networked package depends on: it owns the HttpClient interface, the
// request/response models, HttpClientConfig + DSL + manual validate(), the timeout/retry-hook seams,
// header redaction (a security property, not a feature), and the noop/fake backend. No wire library
// (OkHttp/Ktor) lives here — only the OpenTelemetry *api*, mirroring :observability-api's deps.
dependencies {
    api(project(":observability-api"))
    api(platform(libs.opentelemetry.bom))
    api(libs.opentelemetry.api)
    api(libs.opentelemetry.context)
    api(libs.opentelemetry.extension.kotlin)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real SDK spans so the retry instrumentation (events + http.retry_count) can be asserted end-to-end.
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.sdk.testing)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
