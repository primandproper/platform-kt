# platform-kt

A Kotlin/Android port of [`platform-go`](../platform-go), starting — as it must — with
**observability**. Same philosophy, idiomatic on Android: coroutine-native context propagation,
Logcat and OpenTelemetry backends, and a single `Observer`/`Operation` facade per component.

> **Status:** initial sketch of the observability pillar. Written against JDK 17 + Android SDK;
> build and test it on an Android-equipped machine (see [Building](#building)).

📖 **[Usage guide](docs/USAGE.md)** — bootstrapping, instrumenting components, errors, context
propagation, DI, and testing, with call-site examples.

## The idea

A component holds **one** `o11y` field (an `Observer`), not a logger/tracer pair. Each traced
call runs inside a `span { }` scope that hands you an `Operation`. A value `set` on the operation
lands on **both** the active span and a span-linked logger at once — so it shows up in your trace
*and* correlates your Logcat lines. This is a faithful port of platform-go's `observability`
package; the one big, deliberate divergence is that trace context flows through the **coroutine
context** instead of a threaded `ctx` parameter.

```kotlin
class StreamManager(observers: ObserverFactory) {
    private val o11y = observers.named("stream_manager")

    suspend fun add(groupId: String, memberId: String, stream: Stream) = o11y.span("add") {
        set("group_id" to groupId, "member_id" to memberId)  // both span + logger
        logger.debug("adding stream")
        // a nested suspend call here opens spans that parent under "add" automatically
    }
}
```

Assembling the stack (the `ProvidePillars` analog):

```kotlin
val o11y = Observability {
    serviceName = "my-app"
    logging { provider = LoggingProvider.LOGCAT; level = Level.DEBUG }
    tracing { provider = TracingProvider.OTEL; endpoint = "https://collector:4317"; sampleRatio = 0.1 }
}
val observers: ObserverFactory = o11y.observers
// ... and o11y.shutdown() from Application teardown
```

## Modules

| Module | Type | What |
|---|---|---|
| `:observability-api` | Kotlin/JVM | The contract: `Observer`, `Operation`, `Logger`, `Tracer`, `span { }`, `Keys`, config DSL, noop backends. Depends only on the OTel **api**. |
| `:observability-logcat` | Android lib | `LogcatLogger` over `android.util.Log`. |
| `:observability-otel` | Kotlin/JVM | `OtelTracerProvider`: OTel SDK + OTLP exporter + head sampling + W3C propagation. |
| `:observability-testing` | Kotlin/JVM | `RecordingObserver` test double + framework-neutral matchers. |
| `:observability-koin` | Kotlin/JVM | Optional Koin module (the samber/do analog). |
| `:observability` | Android lib | Umbrella: the `Observability { }` builder that wires the backends. |

Dependencies flow downward: backends and the umbrella depend on `:observability-api`; the api
depends on nothing in this repo.

### Tier 1 — foundation & networking spine

The small, widely-depended-on substrate every other package sits on. All pure Kotlin/JVM (so they
run on both server and Android), coroutine-native where it matters, and traced into the
observability stack.

| Module | Type | What |
|---|---|---|
| `:errors` | Kotlin/JVM | Error sentinels + wrapping (`newError`, `wrap`, `isError`/`asError`), HTTP `ErrorCode`→`toApiError`/`toHttpStatus`, pure gRPC `GrpcCode` mapping. No framework deps. Owns the `ErrCircuitBroken` sentinel. |
| `:identifiers` | Kotlin/JVM | Dependency-free string IDs: `newUlid()`/`isValidUlid()` (26-char, sortable, time-ordered — the `xid` analog) and `newUuid()`/`isValidUuid()` (UUIDv4). |
| `:random` | Kotlin/JVM | Crypto-secure random strings/bytes (`RandomGenerator`) over `SecureRandom` — hex/Base32/Base64url/custom-alphabet encodings, slice helpers — span+log instrumented via `:observability-api`. `noop`/`mock` doubles. |
| `:retry` | Kotlin/JVM | Coroutine-native exponential backoff + jitter: `Policy.execute { }` (`suspend`/`delay`), config DSL + `validate()`, plus a `Flow.retryWhen` variant. |
| `:circuitbreaking` | Kotlin/JVM | Coroutine circuit breaker: `execute { }`, `CircuitState` `StateFlow`, config DSL + `validate()`, noop + recording doubles, and a `partitioned` per-key `KeyedCircuitBreaker`. Depends on `:errors` + `:observability-api`. |
| `:httpclient-api` | Kotlin/JVM | The HTTP contract: `HttpClient`, request/response models, `HttpClientConfig` + DSL + `validate()`, retry-hook seam, **header redaction** (security), OTel span recording, noop/fake client. Depends on `:observability-api` + OTel **api**. |
| `:httpclient-okhttp` | Kotlin/JVM | `HttpClient` over OkHttp, OTel-instrumented. Client-side / Android-friendly backend. |
| `:httpclient-ktor` | Kotlin/JVM | `HttpClient` over the Ktor client (CIO), OTel-instrumented. Server-side backend. |

Traced HTTP calls parent under the enclosing observability `span { }` via the coroutine-context OTel
`Context` — no explicit context threading. Sensitive headers (`Authorization`, `Cookie`, …) are
redacted in `:httpclient-api` **before** any request/response data reaches a span.

## Design notes

- **Context propagation is implicit.** `span { }` installs the span into the coroutine context via
  OTel's `Context.asContextElement()`, so nested `suspend` calls parent correctly across dispatcher
  threads. `spanBlocking { }` is the thread-local variant for non-coroutine call sites
  (`WorkManager`, callbacks, Java interop).
- **Explicit operation names.** There's no cheap analog of Go's `runtime.Callers` caller-naming
  under Kotlin inlining, so `span("name")` takes the name. Terse, and zero reflection on the hot path.
- **Noops everywhere.** A nil logger or tracer degrades to a noop (`ensureLogger`,
  `NoopTracerProvider`); a non-recording span makes every `set`/`error` a no-op. Nothing panics for
  want of observability — mirrors `EnsureLogger` / `NewTracerForTest`.
- **OTel `Span` is aliased, not re-abstracted** (`typealias Span = io.opentelemetry.api.trace.Span`),
  exactly as platform-go aliases `trace.Span`.

### Deliberately descoped (for now)

- **Profiling** — pprof/Pyroscope have no Android analog; the real equivalents are JankStats /
  Macrobenchmark / Firebase Performance. Add a `Profiler` provider when there's a concrete need.
- **Metrics** — to be added once the core feel is validated (the `Provider`/counters/histograms
  port).
- **gRPC status + HTTP logger helpers** — server / `httpclient` concerns, not this module.

## Building

Requires **JDK 17** and the **Android SDK** (`local.properties` with `sdk.dir`, or
`ANDROID_HOME`). Easiest path is to open the project in Android Studio. The Gradle wrapper is
committed, so `./gradlew` works out of the box — from the CLI just use the `Makefile`:

```bash
make build     # compile + assemble
make fmt       # ktlintFormat
make lint      # ktlintCheck
make test      # JVM unit tests
make check     # lint + format + tests — same command CI runs
```

Run `make help` for the full target list.

Every PR is gated by [`.github/workflows/ci.yml`](.github/workflows/ci.yml), which runs `make check`
(ktlint + unit tests + Android Lint). It invokes the same target you run locally, so a green
`make check` on your machine predicts a green CI run.

### Verification

```bash
# Pure-JVM modules — no Android SDK or emulator needed:
./gradlew :observability-testing:test   # set() lands on both pillars; span{} ends once + records throws
./gradlew :observability-otel:test      # span context parents correctly across dispatcher threads (in-memory exporter)
```

End-to-end eyeball: run a local OTel Collector + Jaeger (Docker), point `tracing.endpoint` at it,
fire a couple `span { }` calls from a sample `Activity`, and confirm the trace tree plus the
`trace.id` / `span.id` on the Logcat lines. Build the sample against `minSdk 21` with core library
desugaring enabled to exercise the OTel-on-old-Android path.
