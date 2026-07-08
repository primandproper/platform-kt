// Root build file. Plugins are declared here with `apply false` so each module
// applies the ones it needs against a single resolved version (see gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

group = "com.primandproper.platform"
version = "0.1.0-SNAPSHOT"

// One formatter for the whole tree. ktlintFormat (make fmt) rewrites; ktlintCheck (make lint) and
// the `check` lifecycle verify. Applied here so every module inherits it without per-module wiring.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // Disable kotlinx-coroutines stacktrace recovery under test. Recovery rethrows a *copy* of any
    // exception that crosses a coroutine boundary (`withContext`, `async`, …) with the original as
    // its cause — which breaks the identity that error-correlation tests assert (`assertSame`,
    // `throwable in op.errors`). platform-go checks errors by identity (`errors.Is`); turning
    // recovery off keeps that semantics faithful and test outcomes deterministic across the tree.
    tasks.withType<Test>().configureEach {
        systemProperty("kotlinx.coroutines.stacktrace.recovery", "false")
    }
}
