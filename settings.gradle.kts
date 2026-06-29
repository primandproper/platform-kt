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
)
