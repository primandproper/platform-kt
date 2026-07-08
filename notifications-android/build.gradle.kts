plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Real com.android.library: the RECEIVE side of push notifications (platform-go's `notifications/mobile`
// as consumed ON-DEVICE, direction-flipped from the server send side in `:notifications-fcm`).
//
// It defines the inbound message model (`IncomingPushMessage`), a handler interface
// (`PushMessageHandler`), and the payload-mapping logic (`PushPayloadMapper` / `PushMessageDispatcher`)
// that turns an incoming FCM push into that model and dispatches it — all unit-tested on the JVM,
// including malformed payloads.
//
// TODO(fcm-android): the concrete `FirebaseMessagingService` subclass that Android instantiates on a
// delivered push is a documented seam, NOT implemented here. Pulling `com.google.firebase:firebase-messaging`
// requires a google-services.json + the Google Services Gradle plugin and an instrumented runtime, which
// won't build/run in this JVM-only setup. The service subclass is a thin adapter: its
// `onMessageReceived(RemoteMessage)` copies the RemoteMessage fields into a `RawPushPayload` and calls
// `PushMessageDispatcher.dispatch`; its `onNewToken(String)` calls `dispatchToken`. All the testable
// logic lives here behind that seam, exactly as `:analytics-android` keeps the Segment-Kotlin SDK behind
// its buffering reporter.
android {
    namespace = "com.primandproper.platform.notifications.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        // opentelemetry-api (transitive via :notifications-api -> :observability-api) references
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
    api(project(":notifications-api"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}
