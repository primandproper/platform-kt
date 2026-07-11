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
// The concrete `FirebaseMessagingService` subclass Android instantiates on a delivered push is
// `PlatformFirebaseMessagingService`. It is a thin adapter: `onMessageReceived(RemoteMessage)` copies
// the RemoteMessage fields into a `RawPushPayload` and calls `PushMessageDispatcher.dispatch`;
// `onNewToken(String)` calls `dispatchToken`. All the mapping/dispatch logic lives behind that seam
// in JVM-unit-tested code (`PushMessageDispatcher`/`PushPayloadMapper`); only the service class touches
// a `com.google.firebase` type. The `firebase-messaging` AAR compiles and merges its manifest without
// google-services.json; a consuming app supplies its own `google-services.json` + Google Services
// Gradle plugin and registers a `PushMessageHandler` via `PlatformFirebaseMessaging.handler` (or by
// having its `Application` implement `PushMessageHandlerProvider`).
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

    // The receive-side FCM SDK. `implementation`, not `api`: only PlatformFirebaseMessagingService
    // touches a com.google.firebase type — callers interact through PushMessageHandler / the payload
    // model, which stay Firebase-free so the dispatch logic is JVM-unit-testable.
    implementation(libs.firebase.messaging)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}
