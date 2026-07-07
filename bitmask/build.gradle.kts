plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM, stdlib only. Port of platform-go's `bitmask` package — an immutable bitmask with
// set/clear/toggle/has/union/intersect/difference operations that each return a new value.
//
// platform-go parameterises `Bitmask[T Unsigned]` over the four fixed-width unsigned integer types;
// Kotlin can't express that constraint (its unsigned types share no numeric supertype you can run
// bit ops against generically), so the type parameter's one real job — carrying the bit width used
// for zero-padded `toString` and range-checked parsing — becomes an explicit `width` field over a
// single `ULong` backing store. See the doc comment on `Bitmask` for how this overlaps with, and why
// it is preferred to, `java.util.EnumSet` / raw `Long` bit twiddling.
dependencies {
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
