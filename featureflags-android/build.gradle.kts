plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Android backend for the FeatureFlagManager contract. Ships two backends behind the same interface:
// a DataStore-preferences-backed local flag store (a real, persistable Android backend, unit-testable
// off-device over a temp-file PreferenceDataStore, no Context required), and a LaunchDarkly-backed
// manager over the LaunchDarkly Android client SDK. The LaunchDarkly manager requires an Application
// context and network init, so it is exercised on-device rather than in the off-device unit build.
android {
    namespace = "com.primandproper.platform.featureflags.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        // opentelemetry-api (transitive via :observability-api) references java.time / java.util.function.
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
    api(project(":featureflags-api"))
    api(project(":observability-api"))

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // LaunchDarkly Android client SDK (on-device flag backend). `implementation`: callers evaluate
    // through the platform-owned FeatureFlagManager / EvaluationContext, which stay vendor-free.
    implementation(libs.launchdarkly.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

tasks.withType<Test> { useJUnitPlatform() }
