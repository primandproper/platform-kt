plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the QR-code Builder contract, its config, and the noop double (port of platform-go's
// `qrcodes` and `qrcodes/noop`). Renders the authenticator `otpauth://totp/...` URI for a TOTP
// username/secret pair into a QR-code PNG and returns it as a base64 `data:image/png;base64,...` URI,
// ready to drop into an `<img src>`.
//
// observability-api is `api`: the production builder genuinely instruments every BuildQRCode call (an
// Observer span, the username and otpauth-URI length recorded on both pillars), and
// `Observer`/`Logger`/`TracerProvider` show up in its public constructor — matching how `:cache-api`
// carries observability-api.
//
// Coroutines are `api`: `buildQrCode` suspends (Go threads a `context.Context`; Kotlin suspends
// instead), so `kotlinx.coroutines` types are part of the public surface.
//
// ZXing is `implementation`: `QRCodeWriter` -> `BitMatrix` -> PNG via `MatrixToImageWriter` is an
// internal detail; only the base64 data-URI String crosses the module boundary, so ZXing stays off the
// public API. Its coordinates are declared directly here rather than in the version catalog because
// they are this module's private vendor dependency (`core` encodes, `javase` writes the PNG).
dependencies {
    api(project(":observability-api"))
    api(libs.kotlinx.coroutines.core)

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
