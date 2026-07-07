plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Android backend for the FeatureFlagManager contract. Ships a DataStore-preferences-backed local
// flag store — a real, persistable Android backend that is unit-testable off-device (the
// PreferenceDataStore is created over a temp file, no Context required). The LaunchDarkly Android
// client SDK is left as a documented TODO(launchdarkly-android) seam: it requires an Application
// context and network init, so it is impractical to exercise in an off-device unit build.
android {
    namespace = "com.primandproper.platform.featureflags.android"
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
    api(project(":featureflags-api"))
    api(project(":observability-api"))

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

tasks.withType<Test> { useJUnitPlatform() }
