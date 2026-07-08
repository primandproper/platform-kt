plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. A single module mirroring platform-go's `authentication` package and its
// subpackages — `argon2` (password hashing), `tokens` (JWT issuer + config + noop/mock doubles),
// and `totp` (RFC 6238 second-factor verifier + provisioning URI). Everything is instrumented
// through :observability-api exactly like platform-go threads its `observability.Observer`.
//
// Argon2id is provided by BouncyCastle's pure-JVM `Argon2BytesGenerator`, which produces the same
// standard Argon2id output x/crypto does — so the encoded-hash wire format and the known-answer
// vector from platform-go round-trip byte-for-byte. TOTP and the JWT signer lean entirely on the
// JDK (`javax.crypto` HMAC-SHA1/256, `java.security.SecureRandom`, `java.util.Base64`), so no vendor
// is needed there.
//
// Coroutines are `api`: every issuer/verifier/hasher method suspends (platform-go threads a
// `context.Context`; this port suspends instead), so `kotlinx.coroutines` types are part of the
// public surface. :errors is `api` because the token/totp sentinel `PlatformException`s appear in
// the public API. :observability-api is `api` because the production `Observer` shows up in the
// public factory signatures, matching how :cryptography-jvm carries it.
dependencies {
    api(project(":observability-api"))
    api(project(":errors"))
    api(libs.kotlinx.coroutines.core)

    implementation(project(":identifiers"))

    // Argon2id backend. Pure-JVM and deterministic in tests, matching x/crypto's standard Argon2id.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

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
