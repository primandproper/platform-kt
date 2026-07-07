plugins {
    alias(libs.plugins.kotlin.jvm)
}

// LaunchDarkly server backend for the FeatureFlagManager contract. Wraps the LaunchDarkly Java
// server SDK behind the platform-owned interface and instruments each evaluation through
// :observability-api. The SDK can initialize fully offline (via its TestData data source), so the
// mapping/adaptation logic is unit-tested without a live LaunchDarkly connection. Port of
// platform-go's `featureflags/launchdarkly` package.
dependencies {
    api(project(":featureflags-api"))
    api(project(":observability-api"))
    api(project(":circuitbreaking"))

    // LaunchDarkly Java server SDK (primary server backend).
    implementation("com.launchdarkly:launchdarkly-java-server-sdk:7.6.0")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
