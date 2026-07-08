plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The Elasticsearch text-search backend (port of platform-go's `search/text/elasticsearch`). Depends
// on `:search-api` for the `Index<T>`/`DocumentCodec` contract it implements and the noop the
// `provideTextIndex` factory falls back to, and on `:observability-api` because every operation opens
// an Observer span (the type shows up in the public constructor).
//
// A live Elasticsearch cluster is NOT required to build or unit-test: the index-management logic is
// exercised against a fake `ElasticsearchClient`, mirroring how `:cache-redis` tests against a fake
// Redis client. The production adapter (`LowLevelElasticsearchClient`) drives the Elastic JVM
// client's low-level REST transport and connects lazily.
//
// New third-party coordinates are declared directly here (not in the version catalog) to keep
// parallel module work mergeable — the Elastic Java client 8.15 stack: the typed client artifact, its
// low-level REST transport (raw-JSON requests match this backend's string boundary), and Jackson for
// pulling `_source` bodies out of a search response.
dependencies {
    api(project(":search-api"))
    api(project(":observability-api"))
    implementation(project(":errors"))
    implementation("co.elastic.clients:elasticsearch-java:8.15.0")
    implementation("org.elasticsearch.client:elasticsearch-rest-client:8.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":observability-testing"))
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }
