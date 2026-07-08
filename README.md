# platform-kt

A batteries-included **Kotlin platform library** — the Kotlin/JVM (and, where it makes sense,
Android) port of [`platform-go`](../platform-go). It's the substrate you build a service on before
you write any business logic: observability, HTTP, resilience, caching, a database/router/server
spine, a message queue, auth, LLM/embeddings clients, and a couple dozen more — each behind a small,
consistent interface, traced and log-correlated end to end.

> **Status:** the port is complete. **38 packages across 73 Gradle modules**, Tiers 1–4, building,
> testing and linting green (**1,410 tests, 0 failures**). Pure-JVM modules are consumable from any
> server/JVM project **with no Android tooling** (see [Using platform-kt](#using-platform-kt-in-your-project)).

**Jump to:** [What it gives you](#what-it-gives-you) · [Quick taste](#quick-taste) ·
[Module catalog](#module-catalog) · [Using it](#using-platform-kt-in-your-project) ·
[Building](#building) · **Guides:** [cookbook](docs/EXAMPLES.md) ·
[observability](docs/USAGE.md) · [porting status](docs/PORTING_STATUS.md)

## What it gives you

Every package follows the **same anatomy**, so once you've learned one you've learned them all:

- **An `-api` module** — the interface you program against, a config DSL that `validate()`s at
  construction (so misconfiguration fails loudly at startup, not silently at first use), and `noop` +
  `mock` doubles for tests.
- **One or more backend modules** — the real implementation over a vendor SDK (Redis, Postgres, S3,
  Stripe, Anthropic, …). Additional vendors are documented `TODO(<vendor>)` seams, not silent gaps.

Three properties run through the whole tree:

1. **Traced & correlated.** A value you record on an operation lands on **both** its trace span and a
   span-linked logger, and downstream HTTP/DB/queue calls parent under it automatically — via the
   **coroutine context**, with no `ctx` parameter to thread. Observability is the founding pillar
   everything else plugs into.
2. **Resilient by construction.** Every remote call accepts an optional circuit breaker; retries,
   backoff, and rate limiting are first-class and coroutine-native.
3. **Noops everywhere.** Telemetry, breakers, and backends all degrade to no-ops when absent —
   nothing throws for want of a logger. You wire in exactly what you need.

**Dual-target.** `platform-go` is backend-only; Kotlin runs on both the server *and* Android, so the
pure-JVM modules serve both, and a handful of packages add an Android counterpart — sometimes with the
data flow **flipped** (`eventstream` *emits* SSE/WS on the server but *consumes* it on Android;
`notifications` *sends* push server-side but *receives* it on-device; `uploads` adds a signed-URL
client uploader).

## Quick taste

The founding pillar — one `Observer` per component, a `span { }` per traced call, and a `set` that
writes to the span **and** the correlated log line at once:

```kotlin
class UserRepository(observers: ObserverFactory, private val api: UserApi) {
    private val o11y = observers.named("user_repository")

    suspend fun load(userId: String): User = o11y.span("load") {
        set(Keys.USER_ID to userId)          // lands on the span AND the span-linked logger
        logger.debug("loading user")
        api.fetch(userId)                     // if api is instrumented, its span nests under "load"
    }                                         // span ends on return; any throw is recorded + logged
}
```

A few more packages, to show the house style (full cookbook in **[docs/EXAMPLES.md](docs/EXAMPLES.md)**):

```kotlin
// Resilient, traced HTTP — OkHttp (client/Android) or Ktor (server) behind one interface
val http: HttpClient = OkHttpHttpClient.create(HttpClientConfig { timeout = 5.seconds })
val resp = http.execute(HttpRequest.get("https://api.example.com/health"))

// A circuit breaker around a flaky dependency
val breaker = CircuitBreaker { failureThreshold = 5; resetTimeout = 10.seconds }
val data = breaker.execute { dependency.call() }        // throws ErrCircuitBroken while OPEN

// A typed cache — swap InMemoryCache for a Redis-backed one with the same interface
val cache: BatchCache<String> = InMemoryCache()
cache.set("greeting", "hello"); cache.get("greeting")   // "hello"

// A Claude completion (key redacted from telemetry)
val llm = AnthropicLlmProvider(apiKey = key, httpClient = http)
val answer = llm.complete(CompletionParams(
    model = AnthropicModels.OPUS_4_8,
    messages = listOf(Message(Role.USER, "Say hi.")),
)).content
```

## Module catalog

73 modules, grouped by the tier they were ported in. Depend on just the ones you need — every JVM
module depends only on other JVM modules. Coordinates are
`com.github.primandproper.platform-kt:<module>:<tag>` (see [Using it](#using-platform-kt-in-your-project)).

### Observability — the founding pillar

| Module | Type | What |
|---|---|---|
| `:observability-api` | JVM | The contract: `Observer`, `Operation`, `Logger`, `Tracer`, `span { }`, `Keys`, config DSL, noop backends. |
| `:observability-otel` | JVM | OTel SDK + OTLP exporter + head sampling + W3C propagation. |
| `:observability-logcat` | Android | `LogcatLogger` over `android.util.Log`. |
| `:observability-koin` | JVM | Optional Koin module for DI. |
| `:observability-testing` | JVM | `RecordingObserver` test double + framework-neutral matchers. |
| `:observability` | Android | Umbrella `Observability { }` builder that wires the backends. |

Full walkthrough: **[docs/USAGE.md](docs/USAGE.md)**.

### Tier 1 — foundation & networking spine

The small, widely-depended-on substrate. All pure Kotlin/JVM, coroutine-native, traced.

| Module | What |
|---|---|
| `:errors` | Sentinels + `wrap`/`isError`/`asError`; HTTP `ErrorCode`→`toApiError`/`toHttpStatus`; gRPC `GrpcCode` mapping. Owns `ErrCircuitBroken`. |
| `:identifiers` | Dependency-free `newUlid()` (sortable, time-ordered) + `newUuid()`. |
| `:random` | Crypto-secure random strings/bytes over `SecureRandom` (hex/Base32/Base64url/custom alphabet). |
| `:retry` | Coroutine-native exponential backoff + jitter: `Policy.execute { }` and a `Flow.retryWithPolicy` variant. |
| `:circuitbreaking` | `execute { }` breaker with a `CircuitState` `StateFlow`, plus a `partitioned` per-key registry. |
| `:httpclient-api` | The HTTP contract: request/response models, config DSL, retry hook, **header redaction**, OTel span recording. |
| `:httpclient-okhttp` / `:httpclient-ktor` | OkHttp (client/Android) and Ktor/CIO (server) backends behind that one contract. |

### Tier 2 — core cross-surface services

Each ships core `-api` + one primary backend + a real Android module.

| Module(s) | What |
|---|---|
| `:cache-api` · `:cache-redis` · `:cache-android` | Typed `Cache`/`BatchCache`: in-memory + Redis (Lettuce, breaker-wrapped) + DataStore on Android. |
| `:cryptography-api` · `:cryptography-jvm` · `:cryptography-android` | AES-256-GCM AEAD + SHA/checksum hashers; AndroidKeyStore backend. |
| `:secrets-api` · `:secrets-android` | `SecretSource`: env provider (value never touches a span/log) + EncryptedSharedPreferences. |
| `:featureflags-api` · `:featureflags-launchdarkly` · `:featureflags-android` | Flag evaluation: in-memory + LaunchDarkly (breaker-wrapped) + local store. |
| `:analytics-api` · `:analytics-segment` · `:analytics-android` | Event reporting: Segment + `multisource` composite + on-device buffering. |

### Tier 3 — server platform

The near-1:1 Kotlin server counterpart to `platform-go`, retargeted onto the JVM ecosystem.

| Module(s) | What |
|---|---|
| `:server-api` · `:server-ktor` | HTTP server over Ktor/Netty; `MountableHandler` keeps the router↔server split. |
| `:routing-api` · `:routing-ktor` | Framework-independent router + `RouteParamManager`, backed by Ktor routing. |
| `:database-api` · `:database-exposed` | `DatabaseClient`/`SqlQueryExecutor` over Postgres/Exposed (H2 in tests). |
| `:ratelimiting-api` · `:ratelimiting-redis` | Token-bucket `RateLimiter`: in-memory + Redis sliding window. |
| `:messagequeue-api` · `:messagequeue-redis` | Publisher/consumer over Redis pub/sub (breaker-wrapped). |
| `:distributedlock-api` · `:distributedlock-redis` · `:distributedlock-postgres` | `Locker`/`Lock`: in-memory + Redis SET-NX + Postgres advisory locks. |
| `:email-api` · `:email-resend` | `Emailer` over Resend REST, with recipient-injection defenses. |
| `:uploads-api` · `:uploads-s3` · `:uploads-android` | `UploadManager`/`Bucket`: in-mem/filesystem + S3 + signed-URL client uploader. |
| `:eventstream-api` · `:eventstream-ktor` · `:eventstream-android` | SSE/WS: emit on the server, consume into a `Flow` on Android. |
| `:search-api` · `:search-elasticsearch` · `:search-pgvector` | Text + vector search over Elasticsearch and pgvector. |
| `:capitalism-api` · `:capitalism-stripe` | Payments over stripe-java, with offline webhook-signature verification. |
| `:healthcheck` · `:cookies` · `:encoding` | Concurrent health checks · AES-GCM sealed cookies · kotlinx.serialization JSON. |

### Tier 4 — domain, AI & utilities

| Module(s) | What |
|---|---|
| `:llm-api` · `:llm-anthropic` | Chat completions over the Anthropic Messages API (current Claude models; key redacted). |
| `:embeddings-api` · `:embeddings-openai` | Text embeddings over OpenAI (breaker-wrapped). |
| `:authentication` | Argon2id password hashing + HS256 JWTs + RFC 6238 TOTP. |
| `:notifications-api` · `:notifications-fcm` · `:notifications-android` | Push via FCM HTTP v1 (send) + Android receive-side mapping. |
| `:qrcodes` | ZXing PNG QR codes for `otpauth://` URIs (pairs with TOTP). |
| `:compression` · `:files` · `:numbers` · `:bitmask` | zstd/S2 · nio line/chunk readers · BigDecimal helpers · width-typed bitmasks. |
| `:version` · `:fake` · `:testutils` | Build/VCS metadata · seedable data generator · Testcontainers helpers. |

The authoritative per-package status — which backends shipped and which vendor seams remain open — is
**[docs/PORTING_STATUS.md](docs/PORTING_STATUS.md)**.

## Using platform-kt in your project

platform-kt publishes through [JitPack](https://jitpack.io) — no artifact registry to log into and no
credentials. To cut a release, make the GitHub repo public and push a tag:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Then, in the consuming project, add the JitPack repository and depend on the modules you want by
coordinate — `com.github.primandproper.platform-kt:<module>:<tag>`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.primandproper.platform-kt:cache-api:v0.1.0")
    implementation("com.github.primandproper.platform-kt:cache-redis:v0.1.0")
}
```

JitPack builds the tag on first request (later resolves are cached and fast). It compiles only the
**pure-JVM** modules — the Android-library modules are excluded via `-PjvmOnly` (see `jitpack.yml` and
the toggle in `settings.gradle.kts`) — so **a server/JVM project consumes platform-kt with no Android
tooling on either side**, and JitPack itself never needs the Android SDK. Every JVM module depends
only on other JVM modules, so no `.aar` is ever pulled into a server build.

> Want the Android (`.aar`) modules on JitPack too? Drop `-PjvmOnly` from `jitpack.yml`; JitPack's
> build image must then supply the Android SDK for the project's `compileSdk`.

### Local iteration without a tag

To try changes before tagging, publish to your Maven Local repo and consume via `mavenLocal()`:

```bash
./gradlew publishToMavenLocal            # every module, incl. Android .aar (needs the Android SDK)
./gradlew publishToMavenLocal -PjvmOnly  # JVM modules only, no Android SDK required
```

The coordinate is the same `com.github.primandproper.platform-kt:<module>` either way, so your
`implementation(...)` lines are identical for JitPack and Maven Local — only the repository differs.

## Building

Requires **JDK 17** and the **Android SDK** (`local.properties` with `sdk.dir`, or `ANDROID_HOME`).
Easiest path is to open the project in Android Studio. The Gradle wrapper is committed, so `./gradlew`
works out of the box — from the CLI just use the `Makefile`:

```bash
make build     # compile + assemble
make fmt       # ktlintFormat
make lint      # ktlintCheck
make test      # JVM unit tests
make check     # lint + format + tests — same command CI runs
```

Run `make help` for the full target list. Every PR is gated by
[`.github/workflows/ci.yml`](.github/workflows/ci.yml), which runs `make check` (ktlint + unit tests +
Android Lint) — the same target you run locally, so a green `make check` predicts a green CI run.

## Design notes

- **Context propagation is implicit.** `span { }` installs the span into the coroutine context via
  OTel's `Context.asContextElement()`, so nested `suspend` calls parent correctly across dispatcher
  threads. `spanBlocking { }` is the thread-local variant for non-coroutine call sites (`WorkManager`,
  callbacks, Java interop).
- **Explicit operation names.** There's no cheap analog of Go's `runtime.Callers` caller-naming under
  Kotlin inlining, so `span("name")` takes the name. Terse, and zero reflection on the hot path.
- **Noops everywhere.** A nil logger or tracer degrades to a noop; a non-recording span makes every
  `set`/`error` a no-op. Nothing panics for want of observability.
- **OTel `Span` is aliased, not re-abstracted** (`typealias Span = io.opentelemetry.api.trace.Span`),
  exactly as platform-go aliases `trace.Span`.
- **Faithful to the source.** Ports are validated by comparison against `../platform-go`, not by
  behavioural equivalence testing against the Go binaries. Deliberate divergences (coroutine-context
  propagation, AES-GCM cookie sealing vs gorilla's wire format, Snappy-framed S2) are documented where
  they occur.

### Deliberately descoped (for now)

- **Metrics & profiling pillars** — named, not silently dropped. When metrics lands,
  `Observability`/`ObservabilityConfig`/provider enums extend back toward Go's 4-pillar shape, and the
  `TODO(metrics)` seams across `circuitbreaking`/`cache`/`database`/`ratelimiting`/… reattach.
- **Three Go idioms** with no worthwhile Kotlin analog — `pointer` (Kotlin nullability obviates it),
  `reflection`, and `panicking` (exceptions + `runCatching` cover it).
