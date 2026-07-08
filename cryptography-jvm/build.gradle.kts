plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. The primary encryption backend: AES-256-GCM via javax.crypto, instrumented
// through :observability-api exactly like platform-go's encryption/aes. Salsa20 is a documented
// TODO(salsa20) seam.
dependencies {
    api(project(":cryptography-api"))
    api(project(":observability-api"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
