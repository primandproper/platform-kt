plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Real com.android.library: a Keystore-backed SecretSource. Secrets live in an
// EncryptedSharedPreferences file whose master key is AES-256-GCM and resides in the Android Keystore
// (androidx.security:security-crypto). The store logic is written against the SharedPreferences
// interface so JVM unit tests exercise it with an in-memory fake; KeystoreSecretStore.create() builds
// the real encrypted store on-device.
android {
    namespace = "com.primandproper.platform.secrets.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // EncryptedSharedPreferences / MasterKey require API 23 (AndroidKeyStore AES-256-GCM), above
        // the shared minSdk of 21 — set directly here so the library manifest merges cleanly.
        minSdk = 23
    }

    compileOptions {
        // opentelemetry-api (transitive via :observability-api) references java.time / java.util.function.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":secrets-api"))
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}
