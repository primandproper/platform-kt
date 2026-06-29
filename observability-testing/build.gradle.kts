plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure JVM test double + matchers. Framework-neutral (assertions throw AssertionError), so it works
// under kotlin.test, JUnit, or assertk without forcing an assertion library — the same spirit as the
// platform-go repo banning a single assertion lib.
dependencies {
    api(project(":observability-api"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
