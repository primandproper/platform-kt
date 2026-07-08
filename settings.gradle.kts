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

include(
    ":observability-api",
    ":observability-logcat",
    ":observability-otel",
    ":observability-testing",
    ":observability-koin",
    ":observability",
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
    ":secrets-android",
    ":analytics-api",
    ":analytics-segment",
    ":analytics-android",
    ":featureflags-api",
    ":featureflags-launchdarkly",
    ":featureflags-android",
    ":cryptography-api",
    ":cryptography-jvm",
    ":cryptography-android",
    ":cache-api",
    ":cache-redis",
    ":cache-android",
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
    ":uploads-android",
    ":eventstream-api",
    ":eventstream-ktor",
    ":eventstream-android",
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
    ":notifications-android",
    ":qrcodes",
    ":compression",
    ":files",
    ":numbers",
    ":bitmask",
    ":version",
    ":fake",
    ":testutils",
)
