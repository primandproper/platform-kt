pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "platform-kt"

// Pure Kotlin/JVM modules. These configure and build with no Android SDK, so they're what
// server/JVM consumers depend on — and what JitPack publishes under `-PjvmOnly` (see below).
include(
    ":observability-api",
    ":observability-otel",
    ":observability-testing",
    ":observability-koin",
    // Tier 1 — foundation & networking spine
    ":errors",
    ":identifiers",
    ":random",
    ":retry",
    ":circuitbreaking",
    ":httpclient-api",
    ":httpclient-okhttp",
    ":httpclient-ktor",
    // Tier 2 — core cross-surface services
    ":secrets-api",
    ":analytics-api",
    ":analytics-segment",
    ":featureflags-api",
    ":featureflags-launchdarkly",
    ":cryptography-api",
    ":cryptography-jvm",
    ":cache-api",
    ":cache-redis",
    // Tier 3 — server platform (Wave A)
    ":database-api",
    ":database-exposed",
    ":routing-api",
    ":routing-ktor",
    ":server-api",
    ":server-ktor",
    ":healthcheck",
    ":cookies",
    ":encoding",
    ":ratelimiting-api",
    ":ratelimiting-redis",
    // Tier 3 — server platform (Wave B)
    ":messagequeue-api",
    ":messagequeue-redis",
    ":distributedlock-api",
    ":distributedlock-redis",
    ":distributedlock-postgres",
    ":email-api",
    ":email-resend",
    ":uploads-api",
    ":uploads-s3",
    ":eventstream-api",
    ":eventstream-ktor",
    ":search-api",
    ":search-elasticsearch",
    ":search-pgvector",
    ":capitalism-api",
    ":capitalism-stripe",
    // Tier 4 — domain, AI & utilities
    ":llm-api",
    ":llm-anthropic",
    ":embeddings-api",
    ":embeddings-openai",
    ":authentication",
    ":notifications-api",
    ":notifications-fcm",
    ":qrcodes",
    ":compression",
    ":files",
    ":numbers",
    ":bitmask",
    ":version",
    ":fake",
    ":testutils",
)

// Android-library modules apply AGP, which requires the Android SDK just to *configure* — so
// including them forces any build (local, CI, or JitPack) to have the SDK installed. Passing
// `-PjvmOnly` skips them: JitPack builds and publishes only the JVM artifacts above, so producing
// the server-consumable artifacts never needs Android tooling. Normal builds (no flag) include
// everything, so `make check` and CI still cover the Android modules. No JVM module depends on any
// of these, so their absence never breaks a JVM build.
if (!startParameter.projectProperties.containsKey("jvmOnly")) {
    include(
        ":observability-logcat",
        ":observability",
        ":secrets-android",
        ":analytics-android",
        ":featureflags-android",
        ":cryptography-android",
        ":cache-android",
        ":uploads-android",
        ":eventstream-android",
        ":notifications-android",
    )
}
