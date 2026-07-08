plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. OkHttp is a plain JVM dependency that also runs on Android (with core library
// desugaring in the consuming app), so — like :observability-otel — this module needs no Android
// plugin. This is the client-side / Android-friendly backend. Tracing goes through OpenTelemetry's
// OkHttp instrumentation, whose call spans parent to whatever OTel Context is current — which, in a
// suspend call under observability's `span { }`, is the enclosing operation span.
dependencies {
    api(project(":httpclient-api"))
    api(platform(libs.opentelemetry.bom))
    implementation(platform(libs.opentelemetry.instrumentation.bom.alpha))

    implementation(libs.okhttp.client)
    implementation(libs.opentelemetry.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.okhttp.mockwebserver)
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
