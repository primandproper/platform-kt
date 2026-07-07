plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the search-index-management contract for BOTH text and vector search, their
// configs, and the noop/mock doubles (port of platform-go's `search/text`, `search/text/noop`,
// `search/text/mock`, `search/text/config`, and the mirror `search/vector` tree). Backend
// implementations (Elasticsearch, pgvector) live in their own modules so this API stays free of any
// vendor client dependency.
//
// Coroutines are `api`: every index operation suspends (Go threads a `context.Context`; Kotlin
// suspends instead), so `kotlinx.coroutines` types are part of the public surface.
//
// `:errors` is `api` because the vector-search sentinels (ErrEmptyEmbedding, ErrDimensionMismatch,
// …) are public `PlatformException` values, exactly as platform-go exports them as `var Err… =
// errors.New(…)`.
dependencies {
    api(project(":errors"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
