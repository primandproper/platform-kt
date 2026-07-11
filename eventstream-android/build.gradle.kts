plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// The CONSUME side, and the direction flip that makes this package interesting: platform-go's
// `eventstream` only ever EMITS (its SSE/WebSocket code is server-side upgraders). On Android the same
// streams are consumed — an app opens the server's SSE or WebSocket endpoint and reads events. This
// module is that client: OkHttp's SSE (`okhttp-sse`) and WebSocket support, each surfaced as a
// `Flow<Event>` decoded with the shared `EventCodec` from `:eventstream-api`.
//
// OkHttp is the natural Android transport (it also underpins `:httpclient-okhttp`). `okhttp-sse` is
// pulled by direct Maven coordinate (not the version catalog) to keep parallel module work mergeable.
// Unit tests run on the JVM against OkHttp's `MockWebServer` — real SSE framing and a real WebSocket
// upgrade, no device, emulator, or Robolectric required.
android {
    namespace = "com.primandproper.platform.eventstream.android"
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
    api(project(":eventstream-api"))
    implementation(libs.okhttp.client)
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation(libs.kotlinx.coroutines.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
