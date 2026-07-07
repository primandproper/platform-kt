plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the secure-cookie manager — a port of platform-go's `cookies` package. Seals a
// cookie value into a signed, encrypted, tamper-evident string and builds the ready-to-set cookie
// carrying the configured security attributes (HttpOnly/Secure/SameSite/Domain/Max-Age).
//
// Rather than re-roll the gorilla/securecookie encrypt-then-MAC that platform-go leans on, the
// manager reuses this repo's AES-256-GCM primitive from `:cryptography-jvm` as its sealing engine.
// GCM is an AEAD: it provides both confidentiality (gorilla's block-key encryption) and integrity
// (gorilla's hash-key MAC) in a single authenticated pass, so tamper detection is built in — exactly
// the security property the Go port relies on. `:cryptography-jvm` is `implementation` because the
// `EncryptorDecryptor` never surfaces in the public API (it lives behind the manager).
//
// `:observability-api` is `api`: the `Observer` shows up in the public `newCookieManager` factory and
// the manager genuinely instruments encode/decode/buildCookie (a span per method, the cookie name
// recorded), mirroring platform-go's `manager` wrapping every method in an `Observer` operation.
//
// Coroutines are `api`: every manager method suspends (Go threads a `context.Context`; Kotlin
// suspends and lets the span flow through the coroutine context instead).
dependencies {
    api(project(":observability-api"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":cryptography-jvm"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
