plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the compressor contract, its config, and two byte-oriented backends — a Zstd
// backend (zstd-jni) and an S2 backend. Port of platform-go's `compression` package, which selects
// between klauspost/compress's zstd and s2 codecs behind a single `Compressor` interface.
//
// The vendor coordinates live DIRECTLY here rather than in the version catalog, matching how the
// other backend modules carry their own driver deps:
//   - com.github.luben:zstd-jni      — the canonical JVM Zstandard binding, a faithful analog of
//                                       Go's klauspost/compress/zstd.
//   - org.xerial.snappy:snappy-java  — the closest JVM analog of Go's klauspost/compress/s2. S2 is
//                                       a Snappy-derived format, and snappy-java's *framed* streams
//                                       implement the standard Snappy framing that S2 extends. The
//                                       two are NOT byte-compatible on the wire (S2 uses its own
//                                       "S2sTwO" chunk framing), so a payload compressed here will
//                                       round-trip within the JVM but will NOT interoperate with a
//                                       Go s2 reader. Exact S2 framing is a documented TODO(s2) seam;
//                                       see S2Compressor.
//
// Neither Go backend threads a `context.Context` or instruments through the observability stack —
// `compressor` is a plain codec with no Observer — so this module carries no observability-api
// dependency. The only cross-module dependency is `:errors`, whose PlatformException the sentinel
// exceptions extend (public supertype ⇒ `api`).
dependencies {
    api(project(":errors"))

    implementation("com.github.luben:zstd-jni:1.5.6-3")
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
