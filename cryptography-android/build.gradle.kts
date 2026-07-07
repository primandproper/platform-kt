plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Real Android library: an EncryptorDecryptor whose key material lives in the AndroidKeyStore,
// provisioned through Jetpack Security (androidx.security:security-crypto). The AES-256-GCM transform
// runs against a non-exportable keystore-backed SecretKey, so plaintext keys never touch the heap.
android {
    namespace = "com.primandproper.platform.cryptography.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        // opentelemetry-api (via :observability-api) and java.util.Base64 reference java.time /
        // java.util APIs that need desugaring on minSdk 21.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":cryptography-api"))
    api(project(":observability-api"))
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> { useJUnitPlatform() }
