plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, stdlib only. Port of platform-go's `version` package — a holder for build/VCS
// metadata (version, commit hash, commit time, build time) with an "unknown" fallback for any unset
// field, plus indented-JSON rendering.
//
// Injection diverges from Go. platform-go populates package-level vars with `-ldflags -X` at link
// time; the JVM has no linker seam, so the equivalent (a Gradle task writing a generated resource or
// stamping the JAR manifest, read at startup into `BuildInfo`) is left as a documented seam. This
// module ships the holder and the unknown-fallback accessor only; wiring the build to fill it is the
// consumer's job. JSON is hand-rolled (no kotlinx.serialization dependency) to match Go's
// two-space-indented `json.Encoder` output byte-for-byte.
dependencies {
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
