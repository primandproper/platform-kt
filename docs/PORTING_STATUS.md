# Porting Status & Game Plan: `platform-go` → `platform-kt`

_Generated 2026-07-06; updated 2026-07-07 (port complete — Tiers 1–4 all landed). Compares the Kotlin
port against its Go source at `../platform-go`._

## Where we stand

**38 packages ported, across 73 Gradle modules** — `observability` (the original), all of
**Tier 1** (foundation & networking spine: `errors`, `identifiers`, `random`, `retry`,
`circuitbreaking`, `httpclient`), all of **Tier 2** (`cache`, `cryptography`, `secrets`,
`featureflags`, `analytics`), all of **Tier 3** — the server platform (`server`, `routing`,
`database`, `messagequeue`, `distributedlock`, `email`, `eventstream`, `uploads`, `search`,
`ratelimiting`, `capitalism`, `healthcheck`, `cookies`, `encoding`), and all of **Tier 4** — domain,
AI & utilities (`llm`, `embeddings`, `authentication`, `notifications`, `qrcodes`, `compression`,
`files`, `numbers`, `bitmask`, `version`, `fake`, `testutils`). Only the three deliberately-skipped Go
idioms remain (`pointer`, `reflection`, `panicking`).

> ✅ **The whole tree builds, tests, and lints green: 1,410 tests, 0 failures across 73 modules**
> (`mise exec -- ./gradlew build`, 2026-07-07). Tiers 3 and 4 were ported by parallel subagents (one
> package each, in waves) writing to disjoint module directories, then integrated and greened centrally
> — new vendor coordinates declared directly in each module's `build.gradle.kts` (Exposed/Postgres, Ktor
> server, AWS S3, stripe-java, Elasticsearch/pgvector, Lettuce, BouncyCastle, ZXing, zstd-jni, Snappy,
> Testcontainers, kotlinx.serialization) so parallel work never touched the shared version catalog.

The machine has a toolchain (tracked `mise.toml`: JDK 17 + Gradle 8.11 + Android SDK); the
`com.android.library` modules build here too (compileSdk/minSdk from the catalog). Build it yourself
with `mise exec -- ./gradlew build`.

Landed with Tier 1 (see the [Tier 1 table](#tier-1--foundation--networking-spine--done) for detail):

- `errors` owns the `ErrCircuitBroken` sentinel (which `:circuitbreaking` re-exports) so the HTTP/gRPC
  mappers can match it by identity without an `:errors → :circuitbreaking` cycle.
- `circuitbreaking` keeps **metrics descoped** — state transitions log through the `Observer`, with a
  documented `TODO(metrics)` reattach seam — consistent with the observability port.
- `httpclient` carries **header redaction** in `:httpclient-api` (redacting `Authorization`/`Cookie`/…
  before any request/response data reaches a span) — the security property this doc called for.

---

**The original port: `observability`** — solid. It faithfully carries the
logging + tracing pillars (Observer/Operation dual-write, span↔log correlation, noop degradation,
OTel backend, Koin DI, a `RecordingObserver` test double), with deliberate, documented divergences
for Android (coroutine-context propagation, explicit span names). Three things to keep on the radar
inside that port:

- **Metrics & profiling pillars are descoped** (named in the README, not silently dropped). When
  metrics lands, `Observability`/`ObservabilityConfig`/provider enums extend back to Go's 4-pillar shape.
- **Header redaction** — ✅ now delivered in `:httpclient-api` (redacts `Authorization`/`Cookie`/etc.
  before span attachment), where the HTTP span integration actually lives. Security property, not a
  feature.
- **Test coverage is thin** (2 files): propagation + recording. `LogcatLogger`, config `validate()`,
  and the umbrella builder are unpinned — the one open test-backfill item (see [Open follow-ups](#open-follow-ups)).

That's the whole of "faithfulness." The rest of this document records **what was ported, in which
tiers, and the follow-ups that remain** — the tables below are the authoritative per-package status.

---

## The reframe: Kotlin is dual-target — JVM server *and* Android

`platform-go` is a backend library. Kotlin runs both server-side (**Ktor, Spring Boot, gRPC-Kotlin,
Exposed/jOOQ, R2DBC**) *and* on **Android**. So `platform-kt` can be the near-1:1 Kotlin counterpart of
`platform-go` on the server, **plus** extend the client-relevant packages to Android. Almost every
package has a home — the question is *which surface* and *what priority*, not whether to port it.

Porting therefore comes in three modes:

1. **Direct port** — translate the abstraction idiomatically (retry, cache, errors, most utils). Applies to both surfaces.
2. **Backend swap** — keep the interface, replace Go's vendor SDKs with JVM/Kotlin equivalents
   (`database` → Exposed/jOOQ, `messagequeue` → JVM Pub/Sub/SQS clients, `email` → JVM SES/SendGrid). This is
   what most **server** packages need.
3. **Client re-orientation** — for a few packages the *direction flips* on Android: `notifications`
   *sends* push server-side but Android *receives* it; `eventstream` *emits* SSE/WS but Android *consumes* it.
   These port **directly on the server** and additionally want a flipped client counterpart on Android.

Three packages were deliberately skipped as Go idioms with no worthwhile Kotlin analog — **`pointer`**
(Kotlin nullability + stdlib obviate it), **`reflection`**, and **`panicking`** (see [Skip](#skip)).
Everything else was ported, `version` included.

**Target legend:** 🖥️ Server (JVM) · 📱 Android · 🌐 Both

---

## Ported packages, by tier (spanning both surfaces)

_The tiers below were the porting order; all four are now complete. They double as the per-package
status record._

### Tier 1 — Foundation & networking spine ✅ DONE

Small, depended-on by nearly everything, and the tracing-aware HTTP client ties straight into the
observability port already in place. **All ported and green.**

| ✅ | Pkg | LOC | Target | Module(s) | Notes on the port |
|:--:|---|--:|:--:|---|---|
| ✅ | **errors** | 457 | 🌐 | `:errors` | Sentinels + `wrap`/`isError`/`asError` over exceptions; HTTP `ErrorCode`→`toApiError`/`toHttpStatus`; pure gRPC `GrpcCode` mapping. Owns `ErrCircuitBroken`. |
| ✅ | **identifiers** | 20 | 🌐 | `:identifiers` | Hand-rolled ULID (the `xid` analog) + `java.util.UUID`. Dependency-free. |
| ✅ | **random** | 482 | 🌐 | `:random` | `SecureRandom` generator (hex/Base32/Base64url/custom-alphabet) + slice helpers; `noop`/`mock` doubles; span+log instrumented. |
| ✅ | **httpclient** | 133 | 🌐 | `:httpclient-api` + `:httpclient-okhttp` + `:httpclient-ktor` | **Keystone.** OkHttp (client/Android) + Ktor (server) backends behind one contract; OTel-traced into observability via the coroutine-context `Context`; header redaction in the api layer. |
| ✅ | **retry** | 185 | 🌐 | `:retry` | Coroutine-native `Policy.execute` (`suspend`/`delay`) + `Flow.retryWhen` variant; equal-jitter math ported bit-for-bit; cancellation-aware. |
| ✅ | **circuitbreaking** | 736 | 🌐 | `:circuitbreaking` | `CircuitState` `StateFlow` breaker + `partitioned` per-key registry; `noop`/recording doubles; metrics descoped (`TODO(metrics)` seam). |

### Tier 2 — Core cross-surface services ✅ DONE

All five ported (2026-07-06) as **core + one primary backend + a real Android module**, per the Go
source's anatomy (interface + config + `noop` + `mock` + in-memory/local). Each package's remaining
vendor backends are documented `TODO(<vendor>)` seams, not silent drops. **234 tests, all green.**

| ✅ | Pkg | LOC | Target | Module(s) | Primary backend · Android · seams |
|:--:|---|--:|:--:|---|---|
| ✅ | **cache** | 1744 | 🌐 | `:cache-api` + `:cache-redis` + `:cache-android` | In-mem + Redis (Lettuce, async→coroutine; CRC16 cluster slots; `:circuitbreaking` wrap, degrades to miss/no-op when open) · DataStore + TTL envelope · `TODO`: metrics, cluster client. |
| ✅ | **cryptography** | 680 | 🌐 | `:cryptography-api` + `:cryptography-jvm` + `:cryptography-android` | AES-256-GCM (`javax.crypto`) + hashers (sha256/512, adler32, hand-rolled CRC-64/FNV KAT-matched to Go) · AndroidKeyStore AES-GCM · `TODO`: salsa20. |
| ✅ | **secrets** | 760 | 🌐 | `:secrets-api` + `:secrets-android` | `env` (value never touches a span/log) · EncryptedSharedPreferences + Keystore (minSdk 23) · `TODO`: gcp, ssm, kubectl. |
| ✅ | **featureflags** | 1169 | 🌐 | `:featureflags-api` + `:featureflags-launchdarkly` + `:featureflags-android` | LaunchDarkly server SDK (offline TestData-tested; `:circuitbreaking` wrap, returns default + `ErrCircuitBroken` when open) · DataStore local store · `TODO`: posthog, LD-android. |
| ✅ | **analytics** | 1172 | 🌐 | `:analytics-api` + `:analytics-segment` + `:analytics-android` | Segment Java SDK (behind an execute-shaped CircuitBreaker) + `multisource` composite · on-device buffering reporter · `TODO`: posthog, segment-android. |

> **Follow-ups from the parallel port:** ✅ `cache-redis` and `featureflags-launchdarkly` now reattach
> the `:circuitbreaking` wrap that platform-go has (2026-07-07) — faithful to each package's open-breaker
> semantics: cache degrades to a miss/no-op, featureflags returns the default value + `ErrCircuitBroken`.
> Still open: the Android modules for `featureflags`/`analytics` use the sanctioned local fallback
> because the LaunchDarkly/Segment client SDKs need a live `Context` — wiring those is the remaining
> Android work.

### Tier 3 — Server platform: the Kotlin server counterpart (backend swap) ✅ DONE

**This is the "keep the server things" bucket** — a near-1:1 mirror of `platform-go`, retargeted from
Go stdlib/vendors to the JVM ecosystem. **All 14 packages ported (2026-07-07)** as core `-api` +
primary backend(s) + a real Android module where the surface is client-relevant, per the Tier 2
anatomy (interface + config + `noop` + `mock` + primary impl). Each package's remaining vendor
backends are documented `TODO(<vendor>)` seams, not silent drops.

| ✅ | Pkg | LOC | Target | Module(s) | Primary backend · seams |
|:--:|---|--:|:--:|---|---|
| ✅ | **server** (http, grpc) | 507 | 🖥️ | `:server-api` + `:server-ktor` | Ktor/Netty HTTP; `MountableHandler` keeps Go's router↔server split · `TODO(grpc-kotlin)`, tls, cors. |
| ✅ | **routing** | 794 | 🖥️ | `:routing-api` + `:routing-ktor` | Ktor routing; framework-independent `RouteParamManager` · `TODO`: metrics, cors. |
| ✅ | **database** | 2879 | 🖥️ | `:database-api` + `:database-exposed` | Postgres via Exposed/JDBC (H2 in tests) · `TODO`: mysql, sqlite, r2dbc, pooling. |
| ✅ | **messagequeue** | 2451 | 🖥️ | `:messagequeue-api` + `:messagequeue-redis` | Redis pub/sub (Lettuce) under a CircuitBreaker · `TODO`: kafka, pubsub, sqs. |
| ✅ | **distributedlock** | 1608 | 🖥️ | `:distributedlock-api` (+in-mem) + `:distributedlock-redis` + `:distributedlock-postgres` | Redis SET-NX + Postgres advisory locks · `TODO`: metrics, cluster. |
| ✅ | **email** | 1347 | 🖥️ | `:email-api` + `:email-resend` | Resend REST over `:httpclient-api`; recipient-injection defense carried · `TODO`: sendgrid/mailgun/mailjet/postmark/ses. |
| ✅ | **eventstream** | 903 | 🌐 | `:eventstream-api` + `:eventstream-ktor` + `:eventstream-android` | Ktor SSE/WS emit · OkHttp SSE/WS **consume**→`Flow` on Android (the direction flip). |
| ✅ | **uploads** | 1861 | 🌐 | `:uploads-api` + `:uploads-s3` + `:uploads-android` | S3 (AWS SDK v2) + in-mem/filesystem buckets · signed-URL client uploader · `TODO`: gcs, r2, b2. |
| ✅ | **search** (text, vector) | 2972 | 🖥️ | `:search-api` + `:search-elasticsearch` + `:search-pgvector` | ES low-level REST + pgvector/JDBC · `TODO`: algolia, qdrant, circuitbreaking. |
| ✅ | **ratelimiting** | 376 | 🌐 | `:ratelimiting-api` + `:ratelimiting-redis` | `x/time/rate` token bucket ported bit-for-bit + Lettuce sliding-window · `TODO`: metrics, cluster. |
| ✅ | **capitalism** | 680 | 🖥️ | `:capitalism-api` + `:capitalism-stripe` | stripe-java; offline webhook-signature verification · `TODO`: metrics. |
| ✅ | **healthcheck** | 205 | 🖥️ | `:healthcheck` | Concurrent checks w/ per-check timeout · `TODO(server)` for the probe endpoint. |
| ✅ | **cookies** | 222 | 🖥️ | `:cookies` | Secure cookie sealing via `:cryptography-jvm` AES-GCM (AEAD, not gorilla-wire-compatible; sealed value capped at a 30-day default). |
| ✅ | **encoding** | 1206 | 🖥️ | `:encoding` | kotlinx.serialization JSON · `TODO`: xml, toml, yaml formats. |

### Tier 4 — Domain, AI & utilities ✅ DONE

**All 12 ported (2026-07-07)** as core `-api` + backend(s) where applicable, per the Tier 2 anatomy.
AI/HTTP backends speak vendor REST over `:httpclient-api` (fake-client tested, no network); remaining
vendors/backends are documented `TODO(<vendor>)` seams.

| ✅ | Pkg | LOC | Target | Module(s) | Primary backend · seams |
|:--:|---|--:|:--:|---|---|
| ✅ | **llm** | 578 | 🌐 | `:llm-api` + `:llm-anthropic` | Anthropic Messages API over `:httpclient-api`, current Claude models (`claude-opus-4-8` default), key redacted · `TODO(openai)`. |
| ✅ | **embeddings** | 835 | 🌐 | `:embeddings-api` + `:embeddings-openai` | OpenAI `/v1/embeddings` over `:httpclient-api` under a CircuitBreaker · `TODO`: cohere, ollama. |
| ✅ | **authentication** | 1194 | 🌐 | `:authentication` (argon2/tokens/totp) | Argon2id (BouncyCastle) + HS256 JWT + RFC 6238 TOTP (javax.crypto), KAT/RFC-vector tested · `TODO(paseto)`. |
| ✅ | **notifications** | 1186 | 🌐 | `:notifications-api` + `:notifications-fcm` + `:notifications-android` | FCM HTTP v1 over `:httpclient-api` · Android receive-side payload mapping · `TODO`: apns, `FirebaseMessagingService`. |
| ✅ | **qrcodes** | 141 | 🌐 | `:qrcodes` | ZXing PNG (otpauth URIs); pairs with authentication/totp. |
| ✅ | **compression** | 158 | 🌐 | `:compression` | Zstd (zstd-jni) + Snappy-framed as the S2 analog (wire-incompat noted) · `TODO(s2)`. |
| ✅ | **files** | 704 | 🌐 | `:files` | java.nio line/chunk/slice readers (Go `\n`/`\r` semantics preserved). |
| ✅ | **numbers** | 96 | 🌐 | `:numbers` | BigDecimal round/scale/scaleToYield (NaN/Inf propagated) + range validation. |
| ✅ | **bitmask** | 149 | 🌐 | `:bitmask` | Immutable width-typed bitmask (ULong-backed; Go's `Bitmask[T Unsigned]` → explicit width). |
| ✅ | **version** | 73 | 🌐 | `:version` | Build/VCS metadata holder + indented-JSON; ldflags→resource/manifest injection seam. |
| ✅ | **fake** | 46 | 🌐 | `:fake` | Seedable kotlin-reflect data generator (deterministic, no faker lib). |
| ✅ | **testutils** | 265 | 🖥️ | `:testutils` | Test harness + Testcontainers (Redis/Postgres) helpers; container lifecycle needs live Docker. |

### Skip

Go idioms with no worthwhile Kotlin analog:

- **pointer** (54 LOC) — Kotlin nullability + stdlib (`?.`, `let`, `takeIf`) make it unnecessary.
- **reflection** (333 LOC) — Go `reflect` struct/tag introspection; `kotlin-reflect` is a different model and porting these utils is a smell. Reach for annotation processing / `kotlinx.serialization` instead.
- **panicking** (165 LOC) — an abstraction over Go `panic`/`recover`; Kotlin's exceptions + `runCatching` cover the testing use case directly.

---

## Sequence taken

The port ran foundation-first, then fanned out: **Tier 1** (spine) → **Tier 2** (core cross-surface
services) → **Tier 3** (server platform, ported by parallel subagents in two waves — server spine +
quick wins, then services) → **Tier 4** (domain, AI & utilities, one wave). Each tier was integrated
and greened centrally before the next began. Outcome: a traced, resilient, correlated substrate with a
near-1:1 Kotlin-server counterpart to `platform-go` plus the client re-orientations on Android
(`eventstream` consume, `notifications` receive, `uploads` signed-URL).

## Open follow-ups

Nothing left to **port** (bar the three intentional [skips](#skip)). What remains is refinement:

1. **Backfill observability tests** — `LogcatLogger`, config `validate()`, and the umbrella builder are
   still unpinned in the original `observability` port.
2. **Cross-cutting seams** (documented `TODO(...)` in-code, not silent drops):
   - **Metrics pillar** — observability-api has no metrics surface yet, so every `TODO(metrics)`
     (circuitbreaking, cache, database, ratelimiting, capitalism, …) waits on it. Landing it also
     extends `Observability`/`ObservabilityConfig` back toward Go's 4-pillar shape.
   - **Additional vendor backends** — each package ships one primary backend + named seams: e.g.
     `llm`→openai, `embeddings`→cohere/ollama, `email`→sendgrid/mailgun/mailjet/postmark/ses,
     `database`→mysql/sqlite/r2dbc, `messagequeue`→kafka/pubsub/sqs, `uploads`→gcs/r2/b2 + S3 presigned
     URLs, `search`→algolia/qdrant, `notifications`→apns, `server`→grpc-kotlin, `authentication`→paseto.
   - **Android live-SDK wiring** — `featureflags`/`analytics` Android modules use the sanctioned local
     fallback; the LaunchDarkly/Segment client SDKs need a live `Context`. `notifications-android` leaves
     the concrete `FirebaseMessagingService` as a seam.
   - **Wire-format divergences** (faithful abstractions, not drop-in wire replacements): `compression`'s
     Snappy-framed stands in for Go's klauspost/s2, and `cookies` seals via AES-GCM AEAD rather than
     gorilla/securecookie's format.

> ⚠️ Package purposes are read from each Go package's own doc comment; adequacy of a port is by source
> comparison against `../platform-go`, not by behavioral equivalence testing against the Go binaries.
