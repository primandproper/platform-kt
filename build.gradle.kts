// Root build file. Plugins are declared here with `apply false` so each module
// applies the ones it needs against a single resolved version (see gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

// Published under the JitPack coordinate `com.github.<owner>.<repo>` so that every module's POM —
// including its dependencies on sibling modules — already carries the group JitPack serves. That
// leaves JitPack only the version to substitute (from the git tag, which it does reliably) and
// sidesteps its unpredictable inter-module *group* rewriting (jitpack.io#4112). Consumers then use
// `com.github.primandproper.platform-kt:<module>:<tag>`. Override with `-PpublishGroup=…` for a
// different repository (e.g. a future Maven Central release under com.primandproper.platform).
group = (findProperty("publishGroup") as String?) ?: "com.github.primandproper.platform-kt"
version = "0.1.0-SNAPSHOT"

// One formatter for the whole tree. ktlintFormat (make fmt) rewrites; ktlintCheck (make lint) and
// the `check` lifecycle verify. Applied here so every module inherits it without per-module wiring.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // Every module carries the same Maven coordinates as the root. Without this a sub-module's
    // group is empty, so nothing can resolve it by `com.primandproper.platform:<module>` — neither
    // a composite build's dependency substitution nor a Maven publication. (JitPack overrides these
    // with `com.github.<owner>.<repo>:<module>:<tag>` at build time; they matter for the local and
    // mavenLocal paths.)
    group = rootProject.group
    version = rootProject.version

    // maven-publish gives each module `publishToMavenLocal` (and any remote repo you add). The
    // software component to publish differs by module type — `release` for an Android library,
    // `java` for a Kotlin/JVM module — so we create the publication inside each plugin's reaction
    // block. Registering there (rather than in a single root `afterEvaluate`) guarantees the
    // component exists by the time we read it: the Android plugin only creates `release` after the
    // module opts a variant into publishing, which it can't do from the root's earlier afterEvaluate.
    apply(plugin = "maven-publish")

    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
            publishing { singleVariant("release") }
        }
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications { create<MavenPublication>("maven") { from(components["release"]) } }
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications { create<MavenPublication>("maven") { from(components["java"]) } }
            }
        }
    }

    // Disable kotlinx-coroutines stacktrace recovery under test. Recovery rethrows a *copy* of any
    // exception that crosses a coroutine boundary (`withContext`, `async`, …) with the original as
    // its cause — which breaks the identity that error-correlation tests assert (`assertSame`,
    // `throwable in op.errors`). platform-go checks errors by identity (`errors.Is`); turning
    // recovery off keeps that semantics faithful and test outcomes deterministic across the tree.
    tasks.withType<Test>().configureEach {
        systemProperty("kotlinx.coroutines.stacktrace.recovery", "false")
    }
}
