plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the SecretManager interface (`SecretSource`), its config/provider selection, the
// `noop` and `mock` doubles, and the primary `env` backend that reads from the process environment.
// Instrumented through observability-api exactly like `:random` — each retrieval opens a span and
// records ONLY the lookup key. The security property (secret values never reach a span or log) is the
// secrets analog of httpclient's header redaction, enforced by never handing the value to the pillars.
//
// gcp / ssm / kubectl from platform-go are documented TODO(<vendor>) seams in SecretsConfig, not
// implemented here (their SDKs are server-side and heavy). `api(observability-api)` because the
// Observer/Logger/TracerProvider types show up on EnvSecretSource's public constructor.
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
