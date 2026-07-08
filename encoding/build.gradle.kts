plugins {
    alias(libs.plugins.kotlin.jvm)
    // kotlinx.serialization is compile-time / reflection-free: the compiler plugin synthesizes a
    // `KSerializer<T>` for every `@Serializable` type. It is the direct counterpart to Go's
    // reflection-driven `encoding/json` here — see the module note in ContentType.kt for the full
    // interface{}→KSerializer divergence. Pinned to the catalog's Kotlin version (2.1.0) so the plugin
    // and the standard library agree; declared inline because vendor coordinates stay out of the
    // shared catalog per the module conventions.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

// Pure Kotlin/JVM: the content-type negotiation abstraction, its config, the client/server
// encoder-decoders, the package-level convenience helpers, and the mock doubles (port of
// platform-go's `encoding`, `encoding/mock`). Depends on observability-api because every
// encode/decode instruments an Observer span and records the payload length / content type on both
// pillars, mirroring platform-go's `serverEncoderDecoder` and `clientEncoder`. The `Observer` type is
// a constructor parameter, so the dependency is `api`, matching `:cache-api`.
//
// kotlinx-serialization-json is `api`: `KSerializer<T>` and the `Json` format appear directly in the
// public method signatures (Go's `any` becomes a `KSerializer<T>` the caller passes in). JSON is the
// one format that ships in-box; XML / TOML / YAML / emoji are documented `TODO(<fmt>)` seams
// (EncodingFormat.kt) because each needs its own kotlinx.serialization format library, whereas Go
// pulls them from BurntSushi/toml, yaml.v3, encoding/xml and ecoji+gob.
//
// `:errors` is `implementation`: the `Must*` helpers wrap an encoding failure with a higher-level
// message before throwing, exactly as Go's `errors.Wrapf` feeds the panicker — but the wrapped
// exception never appears in a signature, so it need not be `api`.
dependencies {
    api(project(":observability-api"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(project(":errors"))

    testImplementation(libs.kotlin.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
