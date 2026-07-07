plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. Holds the encryption interfaces, the Hasher interface, config, the noop/mock
// doubles, and every hashing implementation (all backed by the JDK: java.security.MessageDigest,
// java.util.zip.Adler32, and hand-rolled table-based CRC-64 / FNV-1a that match the Go outputs
// byte-for-byte). The concrete encryptor backends live in :cryptography-jvm and :cryptography-android.
dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
