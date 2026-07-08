plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM contract for feature flag evaluation. Holds the FeatureFlagManager interface, the
// repo-owned EvaluationContext, provider config + validation, and the noop / mock / in-memory
// doubles — everything a caller needs without pulling a vendor SDK. Instruments through
// :observability-api exactly like the rest of the tree. Port of platform-go's `featureflags`,
// `featureflags/noop`, `featureflags/mock`, and `featureflags/config` packages.
dependencies {
    api(project(":observability-api"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
