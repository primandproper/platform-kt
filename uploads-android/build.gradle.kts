plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// A real Android library: the client-side signed-URL uploader. Given a pre-signed PUT URL minted by the
// server (see the `:uploads-s3` signing seam), it streams the object's bytes straight to storage over
// the `:httpclient-api` contract — the app injects the OkHttp backend (`:httpclient-okhttp`), so no
// bytes proxy through the service. This has no platform-go analog (Go's uploads is server-only); it is
// the Android counterpart to the server's `URLSigner` capability.
//
// Depends only on `:httpclient-api` (which re-exports observability-api), so the uploader is instrumented
// with an `Observer` span and testable off-device against the api module's `FakeHttpClient`. Unit tests
// run on the JVM — no device, emulator, Robolectric, or network.
android {
    namespace = "com.primandproper.platform.uploads.android"
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
    api(project(":httpclient-api"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}
