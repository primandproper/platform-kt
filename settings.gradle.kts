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
)
