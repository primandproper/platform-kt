plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM text-file read helpers, a port of the line/chunk/slice reading core of
// platform-go's `files` package. The lazy `lines`/`chunks` helpers operate on any java.io.Reader
// (the caller owns its lifecycle); the `*File` helpers open a java.nio.file.Path, read fully or
// slice, and always close the handle — trading the Go iterator's laziness for guaranteed cleanup
// (pair `lines` with a Reader, or Kotlin's Path.useLines, when true streaming is needed). Android
// callers under scoped storage resolve a Uri to a readable path/stream up front and pass that in;
// this module stays platform-agnostic and never resolves file names itself.
//
// platform-go's observability-instrumented Reader, its async StreamChunks channel, the Dir handle,
// and the encoding-backed Decode helpers are intentionally out of scope for this utility slice. The
// sentinel errors mirror platform-go's and extend PlatformException, so `:errors` is `api`.
dependencies {
    api(project(":errors"))

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
