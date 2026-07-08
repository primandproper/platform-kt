plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Stripe-backed PaymentManager (platform-go's `capitalism/stripe`). Wraps the official stripe-java
// SDK behind the `:capitalism-api` PaymentManager interface: the inbound path verifies a webhook
// signature and hands the decoded event to an optional callback; the outbound paths translate a create
// call into a Stripe params builder and drive it through an injected client seam.
//
// `:capitalism-api` and `:observability-api` are `api` deps — PaymentManager and the Observer-carrying
// factory (logger/tracerProvider) both appear on the public surface. `:errors` is `api` because the
// `ErrApiKeyNotConfigured` sentinel is a public `PlatformException`. The stripe-java SDK is `api` (not
// `implementation`) because the public `EventHandler` seam exposes `com.stripe.model.Event`, mirroring
// how platform-go's `stripe.EventHandler` hands back a `*stripe.Event`.
//
// A live Stripe is NOT required to build or unit-test: webhook signature verification is exercised
// with a locally HMAC-signed payload round-tripped through `Webhook.constructEvent`, and the outbound
// operations run against a stubbed `StripeApiClient` seam (see the tests) — the same "inject a narrowed
// client seam" move `:analytics-segment` makes with its `MessageEnqueuer`.
//
// New third-party coordinate is declared directly here (not in the version catalog) to keep parallel
// module work mergeable.
dependencies {
    api(project(":capitalism-api"))
    api(project(":observability-api"))
    api(project(":errors"))
    api("com.stripe:stripe-java:28.0.0")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
