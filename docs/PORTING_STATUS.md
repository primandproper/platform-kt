# Porting Status & Game Plan: `platform-go` → `platform-kt`

_Generated 2026-07-06. Compares the Kotlin port against its Go source at `../platform-go`._

## Where we stand

**8 of 40 packages ported, across 14 Gradle modules** — `observability` (the original) plus all of
**Tier 1** (foundation & networking spine): `errors`, `identifiers`, `random`, `retry`,
`circuitbreaking`, and `httpclient`.

> ⚠️ **Tier 1 is written but not yet compiled or tested.** This machine has no JVM/Android
> toolchain, so the ~4,500 LOC (71 `.kt` files, including tests) was hand-written against the Go
> source and the observability port's conventions — nothing was built or run. Verify on an equipped
> machine with `make setup` then `./gradlew build test ktlintCheck`. The pure-JVM modules
> (`:errors`, `:retry`, `:circuitbreaking`, `:httpclient-api`) need no emulator and are the fastest
> first signal.

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
  and the umbrella builder are unpinned.

That's the whole of "faithfulness." The rest of this document is the part that matters now: **what to
port next, and why.**

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

Only one package is genuinely unnecessary in Kotlin: **`pointer`** (Kotlin nullability + stdlib obviate it).
A handful more are low-value (`reflection`, `panicking`, `version`) but still portable if a consumer needs them.

**Target legend:** 🖥️ Server (JVM) · 📱 Android · 🌐 Both

---

## Game plan — priority tiers (spanning both surfaces)

### Tier 1 — Foundation & networking spine ✅ DONE

Small, depended-on by nearly everything, and the tracing-aware HTTP client ties straight into the
observability port already in place. **All ported (2026-07-06); pending a compile/test pass on a
JVM/Android-equipped machine.**

| ✅ | Pkg | LOC | Target | Module(s) | Notes on the port |
|:--:|---|--:|:--:|---|---|
| ✅ | **errors** | 457 | 🌐 | `:errors` | Sentinels + `wrap`/`isError`/`asError` over exceptions; HTTP `ErrorCode`→`toApiError`/`toHttpStatus`; pure gRPC `GrpcCode` mapping. Owns `ErrCircuitBroken`. |
| ✅ | **identifiers** | 20 | 🌐 | `:identifiers` | Hand-rolled ULID (the `xid` analog) + `java.util.UUID`. Dependency-free. |
| ✅ | **random** | 482 | 🌐 | `:random` | `SecureRandom` generator (hex/Base32/Base64url/custom-alphabet) + slice helpers; `noop`/`mock` doubles; span+log instrumented. |
| ✅ | **httpclient** | 133 | 🌐 | `:httpclient-api` + `:httpclient-okhttp` + `:httpclient-ktor` | **Keystone.** OkHttp (client/Android) + Ktor (server) backends behind one contract; OTel-traced into observability via the coroutine-context `Context`; header redaction in the api layer. |
| ✅ | **retry** | 185 | 🌐 | `:retry` | Coroutine-native `Policy.execute` (`suspend`/`delay`) + `Flow.retryWhen` variant; equal-jitter math ported bit-for-bit; cancellation-aware. |
| ✅ | **circuitbreaking** | 736 | 🌐 | `:circuitbreaking` | `CircuitState` `StateFlow` breaker + `partitioned` per-key registry; `noop`/recording doubles; metrics descoped (`TODO(metrics)` seam). |

### Tier 2 — Core cross-surface services (high value, both server & Android)

| Pkg | LOC | Target | What it does | KT mapping |
|---|--:|:--:|---|---|
| **cache** | 1744 | 🌐 | Generic cache (Redis + in-mem) | Interface + in-mem both surfaces; Redis backend on server, disk (DataStore) on Android. |
| **cryptography** | 680 | 🌐 | Encrypt/decrypt + hashing | `javax.crypto`/Tink server-side; Jetpack Security + Keystore on Android. |
| **secrets** | 760 | 🌐 | Secret retrieval | Keep interface; env/GCP-SM/AWS-SSM backends on server, EncryptedSharedPreferences/Keystore on Android. |
| **featureflags** | 1169 | 🌐 | Flag eval (LaunchDarkly, PostHog) | Both vendors ship server + Android SDKs — wrap behind the platform interface + noop/local backend. |
| **analytics** | 1172 | 🌐 | Event tracking | Segment/PostHog/Amplitude server + Android SDKs behind one interface. |

### Tier 3 — Server platform: the Kotlin server counterpart (backend swap)

**This is the "keep the server things" bucket** — a near-1:1 mirror of `platform-go`, retargeted from
Go stdlib/vendors to the JVM ecosystem. In-scope, just server-first.

| Pkg | LOC | Target | What it does | KT mapping |
|---|--:|:--:|---|---|
| **server** (http, grpc) | 507 | 🖥️ | Multi-service HTTP/gRPC server | Ktor / Spring Boot / gRPC-Kotlin. |
| **routing** | 794 | 🖥️ | Router + middleware + route params | Ktor routing / Spring MVC. |
| **database** | 2879 | 🖥️ | Relational-DB abstraction | Exposed / jOOQ / JDBC / R2DBC. Idiom differs — abstraction ports, impl is fresh. |
| **messagequeue** | 2451 | 🖥️ | Publisher/consumer (Pub/Sub, SQS, Redis) | JVM client libs behind the same publisher/consumer interface. |
| **distributedlock** | 1608 | 🖥️ | Cross-process mutual exclusion | Redis (Lettuce/Jedis) + Postgres advisory locks — direct analog. |
| **email** | 1347 | 🖥️ | Send email (Mailgun/SendGrid/…) | Vendor JVM SDKs / SMTP behind the sender interface. |
| **eventstream** | 903 | 🌐 | SSE / WebSocket | Server emit (Ktor SSE/WS) ports directly; Android additionally wants the **consume** side (OkHttp SSE/WS → `Flow`). |
| **uploads** | 1861 | 🌐 | Object storage (S3/GCS/R2/B2) | Server: AWS/GCP JVM SDKs. Android: signed-URL client uploader over `httpclient`. |
| **search** (text, vector) | 2972 | 🖥️ | Search-index management | ES/OpenSearch + pgvector JVM clients. |
| **ratelimiting** | 376 | 🌐 | Token-bucket limiter | Server endpoint protection; optional client-side throttle. |
| **capitalism** | 680 | 🖥️ | Payment webhooks + subscriptions | Stripe/etc. JVM SDKs server-side (Android in-app purchase is Play Billing — separate). |
| **healthcheck** | 205 | 🖥️ | Component health registry | Direct port; expose via Ktor/Spring actuator-style endpoint. |
| **cookies** | 222 | 🖥️ | Secure cookie encode/manage | Ktor cookie handling. |
| **encoding** | 1206 | 🖥️ | HTTP response encoding | Content negotiation / serializers (kotlinx.serialization). |

### Tier 4 — Domain, AI & utilities (portable; schedule opportunistically)

| Pkg | LOC | Target | What it does | Note |
|---|--:|:--:|---|---|
| **llm** | 578 | 🌐 | LLM completion (Anthropic, OpenAI) | Depends on `httpclient`. Use the latest Claude models via the Anthropic API. |
| **embeddings** | 835 | 🌐 | Vector embeddings | Depends on `httpclient`. |
| **authentication** (argon2, tokens, totp) | 1194 | 🌐 | Password hashing, tokens, TOTP | argon2/tokens server-side; `totp` + token parsing also client-relevant. |
| **notifications** (async, mobile) | 1186 | 🌐 | Push send (APNs/FCM), async delivery | Server *sends*; Android *receives* (`FirebaseMessagingService`) — port both ends. |
| **qrcodes** | 141 | 🌐 | QR generation (TOTP setup) | ZXing. Pairs with `authentication/totp`. |
| **compression** | 158 | 🌐 | Zstd / S2 | JVM zstd bindings; useful under cache/uploads. |
| **files** | 704 | 🌐 | Text file read helpers | Portable; Android scoped-storage caveat. |
| **numbers** | 96 | 🌐 | Numeric utils (round/scale/yield) | Largely covered by stdlib/`BigDecimal`. |
| **bitmask** | 149 | 🌐 | Immutable bitmask | `EnumSet` covers much of it. |
| **fake** | 46 | 🌐 | Test data generation | Port with whatever needs fixtures. |
| **testutils** | 265 | 🖥️ | Integration/load-test harness | Server test support. |
| **version** | 73 | 🌐 | Build/VCS metadata injection | `BuildConfig` (Android) / gradle manifest (server). |

### Skip

Go idioms with no worthwhile Kotlin analog:

- **pointer** (54 LOC) — Kotlin nullability + stdlib (`?.`, `let`, `takeIf`) make it unnecessary.
- **reflection** (333 LOC) — Go `reflect` struct/tag introspection; `kotlin-reflect` is a different model and porting these utils is a smell. Reach for annotation processing / `kotlinx.serialization` instead.
- **panicking** (165 LOC) — an abstraction over Go `panic`/`recover`; Kotlin's exceptions + `runCatching` cover the testing use case directly.

---

## Recommended sequence

**Wave 1 — foundation & spine (both surfaces). ✅ DONE.** `errors`, `identifiers`, `random`,
`httpclient` (keystone, ties into observability), `retry`, `circuitbreaking` — all ported. Outcome:
a traced, resilient, correlated substrate every other package sits on. _Remaining: run the build/test
pass to turn "written" into "verified" (see the ⚠️ note under "Where we stand")._

**Now the path forks by what ships first** — the two tracks are:

- **Server track:** Tier 3 in dependency order — `database` + `errors` underpin most services, then
  `server`/`routing`, then `messagequeue`/`distributedlock`/`email`/`search`/`capitalism`. This makes
  `platform-kt` a real Kotlin-server counterpart to `platform-go`.
- **Android track:** Tier 2 (`cache`, `featureflags`, `analytics`, `secrets`, `cryptography`), then the
  client re-orientations (`eventstream` consume, `notifications` receive, `uploads` signed-URL).

**Shared/AI (`llm`, `embeddings`) slot in after `httpclient`** on either track.

## Things to fix regardless of sequence

1. **Compile & test the Tier 1 modules.** They're hand-written and unbuilt — the single highest-value
   next action. `make setup` then `./gradlew build test ktlintCheck`; expect first-compile friction
   (an import, a catalog version, a `desugar` gap) and iterate.
2. **Backfill observability tests** (`LogcatLogger`, config validation, umbrella builder) — cheapest
   confidence you can buy in the one thing already shipped.
3. ~~**Carry header redaction into `httpclient`**~~ — ✅ done; `:httpclient-api` redacts sensitive
   headers before span attachment.

> ⚠️ Package purposes are read from each Go package's own doc comment; adequacy of a port is by source
> comparison only. Nothing in `platform-kt` has been compiled or run on this machine (no JVM/Android
> toolchain) — Tier 1 especially needs the build/test pass above before it's trustworthy.
