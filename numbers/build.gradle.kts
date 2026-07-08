plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, a port of platform-go's `numbers` package. Rounding goes through
// java.math.BigDecimal with RoundingMode.HALF_UP (half away from zero), matching Go's math.Round
// while sidestepping the float overflow the Go code guards against with a float64 intermediate.
// Kotlin's stdlib already covers clamping (coerceIn / coerceAtLeast / coerceAtMost), so no Clamp
// analog is ported. Range validation maps ozzo-validation onto a plain `require`
// (IllegalArgumentException), which keeps this module dependency-free.
dependencies {
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
