plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Real com.android.library: the on-device analytics reporter. Ships two EventReporter backends:
//
//  - SegmentEventReporter, over the Segment Analytics-Kotlin Android SDK (com.segment.analytics.kotlin:
//    android) — the production delivery backend. It needs a live Context to build the client, so the
//    Context-touching factory can't run in a JVM unit test; the identity/property-mapping logic behind
//    it is unit-tested through a captured SegmentClient seam (see the tests), without network delivery.
//  - BufferingEventReporter — a local, bounded, thread-safe in-memory buffer that implements the same
//    interface, for offline capture / testing / a pre-write-key stand-in.
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

    // Segment Analytics-Kotlin (Android) — the on-device delivery backend. `implementation`: callers
    // use the EventReporter interface, never a com.segment type.
    implementation(libs.segment.analytics.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}
