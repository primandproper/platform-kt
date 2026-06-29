plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Umbrella module: the only place that knows every backend, so it can wire the `Observability { }`
// builder. Android library because it depends on the Logcat backend. Mirrors how platform-go's
// observability/config.go imports each pillar's backend to assemble Pillars.
android {
    namespace = "com.primandproper.platform.observability"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
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
    api(project(":observability-api"))
    api(project(":observability-logcat"))
    api(project(":observability-otel"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
