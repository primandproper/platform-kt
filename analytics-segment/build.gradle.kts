plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Segment-backed EventReporter (platform-go's primary analytics provider). Wraps the Segment
// server-side Java SDK behind the `:analytics-api` EventReporter interface: each method translates
// the call into a Segment `identify`/`track` message and enqueues it, opening an Observer span and
// running the enqueue under an injected circuit breaker.
//
// `:analytics-api` and `:circuitbreaking` are `api` deps because EventReporter and CircuitBreaker
// both appear in the public constructor surface. The Segment SDK is an `implementation` detail — the
// message-building/adaptation logic is unit-tested through a captured enqueuer (see the tests),
// without any network delivery.
//
// TODO(posthog): platform-go also ships a PostHog backend (`analytics/posthog`). It is a documented
// seam here, not implemented — the PostHog SDK would be wired the same way behind EventReporter.
dependencies {
    api(project(":analytics-api"))
    api(project(":circuitbreaking"))
    implementation(project(":observability-api"))

    implementation("com.segment.analytics.java:analytics:3.5.1")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
    testImplementation(project(":identifiers"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
