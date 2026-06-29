plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Optional DI glue. Pure JVM (koin-core), DI-agnostic: it binds an already-assembled Observability
// into a Koin graph. Koin is the service-locator analog of platform-go's samber/do.
dependencies {
    api(project(":observability-api"))
    api(libs.koin.core)

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
