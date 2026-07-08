plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Real com.android.library: the on-device analytics reporter.
//
// TODO(segment-android): the intended production backend is the Segment Analytics-Kotlin Android SDK
// (com.segment.analytics.kotlin:android:1.x), which requires a live android.content.Context and an
// instrumented runtime to deliver events — impractical to exercise in JVM unit tests here. Until that
// is wired on-device, this module ships a local, bounded, thread-safe buffering reporter that
// implements the same EventReporter interface and captures events in memory for later flush. The
// Segment-Kotlin adapter drops in behind the same interface without changing callers.
android {
    namespace = "com.primandproper.platform.analytics.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        // opentelemetry-api (transitive via :analytics-api -> :observability-api) references
        // java.time / java.util.function; on minSdk 21 those need desugaring.
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
    api(project(":analytics-api"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}
