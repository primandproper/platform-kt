plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// A real Android library: a persistent, disk-backed cache built on Jetpack DataStore (Preferences)
// that implements the same `BatchCache` contract as the in-memory and Redis backends. This is the
// Android analog of a server backend — the local, process-death-surviving cache an app reaches for.
//
// DataStore is pulled by direct Maven coordinate (not the version catalog) to keep parallel module
// work mergeable. Unit tests run on the JVM against a fake in-memory `DataStore<Preferences>`, so no
// device, emulator, or Robolectric is required.
android {
    namespace = "com.primandproper.platform.cache.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":cache-api"))
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}
