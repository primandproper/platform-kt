plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM so the contract stays testable off-device and a future KMP move is cheap.
// platform-go's errors package re-exports cockroachdb/errors and pulls in database/sql,
// grpc/codes, and the observability stack; here we depend only on the Kotlin stdlib and map
// those concepts (wrapping, sentinels, HTTP/gRPC status codes) onto plain exceptions and enums.
dependencies {
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
