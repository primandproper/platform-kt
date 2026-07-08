plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the payment-manager contract, its provider-agnostic config, and the noop/mock
// doubles (port of platform-go's `capitalism`, `capitalism/noop`, and `capitalism/mock`). No backend
// and no vendor SDK live here — the Stripe backend, the Stripe-specific config, and the
// `provideCapitalismImplementation` factory that unites them sit in `:capitalism-stripe`, matching how
// `:cache-api` carries the `Cache` contract while `:cache-redis` carries the backend and the
// `provideCache` factory.
//
// Coroutines are `api`: every provider call threads a `context.Context` in Go and returns an `error`;
// the coroutine-native Kotlin analog makes each method `suspend` and signals failure by throwing, so
// `kotlinx.coroutines` types are part of the public surface (the `suspend` marker).
//
// No observability dependency: the interface, config, noop, and mock never instrument — the Observer
// spans live only in the Stripe backend where platform-go instruments (`:capitalism-stripe`), the
// same split `:cache-api` makes (only the in-memory backend there carries `:observability-api`).
dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
