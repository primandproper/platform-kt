plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the push-notification SEND contract (`PushNotificationSender`), the outbound
// message/target value types, the provider config, the standard notification observability keys, the
// noop/mock doubles, and the async event-delivery abstraction (`AsyncNotifier` + an in-memory backend)
// — the port of platform-go's `notifications/mobile`, `notifications/mobile/config`,
// `notifications/mobile/noop`, and `notifications/async`. No backend, no vendor SDK, no network: a
// caller (and a unit test) can depend on the seams without any transport on the classpath, exactly as
// `:email-api` carries the emailer contract above `:email-resend`.
//
// Coroutines are `api`: every send/publish suspends (Go threads a `context.Context`; Kotlin suspends
// instead), so `kotlinx.coroutines` is part of the public surface. `:observability-api` is `api`
// because the in-memory notifier's factory takes an optional root logger/tracer-provider on its
// public surface, matching `:analytics-android`'s BufferingEventReporter.
//
// The real backends live in sibling modules: `:notifications-fcm` (SERVER send over `:httpclient-api`)
// and `:notifications-android` (the RECEIVE side). APNs is a documented `TODO(apns)` seam. Per-provider
// connection config (FCM project id / bearer token) lives with those backends, keeping this module
// transport-free — the same split `:email-api` uses to keep the Resend settings in `:email-resend`.
dependencies {
    api(libs.kotlinx.coroutines.core)
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
