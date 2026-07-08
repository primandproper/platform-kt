plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM. platform-go's `identifiers` package wraps github.com/rs/xid; there is no
// equivalent short-sortable-ID library in the JDK, and pulling one in for ~20 lines of logic isn't
// worth a dependency, so this module hand-rolls a ULID (the xid analog) alongside java.util.UUID.
dependencies {
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
