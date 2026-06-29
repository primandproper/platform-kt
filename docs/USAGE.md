# Using platform-kt observability

A task-oriented guide to wiring the observability stack into an Android app and instrumenting your
code. For the design rationale, see the [README](../README.md); this document is about call sites.

- [Mental model](#mental-model)
- [1. Bootstrap the stack](#1-bootstrap-the-stack-once-per-process)
- [2. Instrument a component](#2-instrument-a-component)
- [3. Inside `span { }`](#3-inside-span--)
- [4. Errors](#4-errors)
- [5. Context propagation](#5-context-propagation)
- [6. Non-coroutine call sites](#6-non-coroutine-call-sites)
- [7. Logging outside an operation](#7-logging-outside-an-operation)
- [8. Dependency injection](#8-dependency-injection)
- [9. Testing](#9-testing)
- [10. Configuration reference](#10-configuration-reference)

## Mental model

Three types carry the whole API:

| Type | Lifetime | You get it from |
|---|---|---|
| `Observability` | one per process | the `Observability { }` builder |
| `Observer` | one per component (repo, manager, view model) | `observability.observers.named("...")` |
| `Operation` | one per traced call | `observer.span("...") { }` (or `begin`) |

A value you `set` on an `Operation` lands on **both** the trace span and a span-linked logger, so it
shows up in your tracing backend *and* on the correlated Logcat line. That dual-write is the point.

## 1. Bootstrap the stack (once per process)

Build one `Observability` in `Application.onCreate`, hold it, tear it down on exit.

```kotlin
class MyApp : Application() {
    lateinit var observability: Observability
        private set

    override fun onCreate() {
        super.onCreate()
        observability = Observability {
            serviceName = "my-app"
            logging {
                provider = LoggingProvider.LOGCAT
                level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
            }
            tracing {
                provider = TracingProvider.OTEL
                endpoint = BuildConfig.OTLP_ENDPOINT   // e.g. "https://collector.example.com:4317"
                sampleRatio = if (BuildConfig.DEBUG) 1.0 else 0.1
            }
        }
    }

    override fun onTerminate() {
        observability.shutdown()   // flushes buffered spans
        super.onTerminate()
    }
}
```

`onTerminate` is not guaranteed on real devices — also call `observability.tracerProvider.forceFlush()`
from a `ProcessLifecycleOwner` `ON_STOP` observer so spans aren't lost when the app is backgrounded.

For local development with no collector, swap the providers to keep everything in Logcat and skip
export entirely:

```kotlin
Observability {
    serviceName = "my-app"
    logging { provider = LoggingProvider.LOGCAT; level = Level.DEBUG }
    tracing { provider = TracingProvider.NOOP }   // spans become no-ops; logging still works
}
```

## 2. Instrument a component

A component takes an `ObserverFactory` and asks for its own named `Observer`. That name becomes the
logger tag and the tracer's instrumentation name.

```kotlin
class UserRepository(
    observers: ObserverFactory,
    private val api: UserApi,
) {
    private val o11y = observers.named("user_repository")

    suspend fun load(userId: String): User = o11y.span("load") {
        set(Keys.USER_ID to userId)
        logger.debug("loading user")

        val user = api.fetch(userId)        // nested suspend calls inherit this span as parent
        set("user.tier" to user.tier)
        user
    }
}
```

The `span("load") { }` block:
- starts a span named `load`,
- installs it as the current trace context for the block (so `api.fetch` — if itself instrumented —
  nests under it),
- ends the span when the block returns or throws,
- records any exception that propagates out.

## 3. Inside `span { }`

The receiver is an `Operation`. The full vocabulary:

```kotlin
o11y.span("checkout") {
    // dual-write: both span attribute and log value
    set("cart_id" to cartId)
    set("item_count" to items.size, "currency" to "USD")   // vararg form

    // single-pillar escape hatches
    spanOnly("raw_request", payload)     // span attribute only (keep noisy data out of logs)
    logOnly("debug_note", "retry path")  // log value only (keep low-value data out of traces)

    // the span-linked logger — every line carries trace.id / span.id automatically
    logger.info("processing checkout")
    logger.debug("cart resolved")

    // direct access if you need them
    val activeSpan: Span = span
    val log: Logger = logger
}
```

`set` returns the `Operation`, so it chains (`set(...).set(...)`) if you prefer the platform-go style.

## 4. Errors

Anything that propagates out of `span { }` is recorded on the span and logged automatically with a
generic message — you don't have to catch it:

```kotlin
suspend fun load(userId: String): User = o11y.span("load") {
    set(Keys.USER_ID to userId)
    api.fetch(userId)   // if this throws, the span is marked errored, the exception logged, then rethrown
}
```

When you want a **specific** message or to annotate before rethrowing, use `error` — it records, logs
your message, and returns the throwable so you can `throw` it:

```kotlin
o11y.span("load") {
    val resp = api.fetch(userId)
    if (!resp.isSuccessful) {
        throw error(HttpException(resp), "user API returned ${resp.code()}")
    }
    resp.body()!!
}
```

When you want to record an error but **not** propagate it (a tolerated failure), use `acknowledge`:

```kotlin
o11y.span("refresh") {
    try {
        cache.warm(userId)
    } catch (e: IOException) {
        acknowledge(e, "cache warm failed; continuing")   // logged + traced, not rethrown
    }
}
```

`error` is the analog of platform-go's `op.Error`; `acknowledge` is `op.Acknowledge`.

## 5. Context propagation

Trace context rides the **coroutine context**, so it flows through `suspend` calls and across
dispatcher hops without being passed explicitly:

```kotlin
o11y.span("outer") {
    withContext(Dispatchers.IO) {
        repo.load(userId)        // the span opened in here parents under "outer"
    }
    coroutineScope {
        launch { repo.audit() }  // so does this one
        launch { repo.notify() }
    }
}
```

The one rule: child coroutines must inherit the current context. `launch`/`async`/`withContext` on
the active scope do. Breaking out to an unrelated scope drops the link:

```kotlin
// ❌ GlobalScope does not inherit the calling coroutine context — the new span won't parent here
GlobalScope.launch { repo.load(userId) }

// ✅ stays within the current context
launch { repo.load(userId) }
```

## 6. Non-coroutine call sites

For code that isn't `suspend` — a `WorkManager` worker, a synchronous callback, Java interop — use
`spanBlocking`, which installs the span via a thread-local scope instead of the coroutine context:

```kotlin
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val o11y = (applicationContext as MyApp).observability.observers.named("sync_worker")

    override fun doWork(): Result = o11y.spanBlocking("doWork") {
        set("attempt" to runAttemptCount)
        logger.info("starting sync")
        // ... synchronous work ...
        Result.success()
    }
}
```

If you need full manual control (rare), `begin`/`end` are the building blocks `span` is sugar over:

```kotlin
val op = o11y.begin("manual")
try {
    op.set("k" to v)
} finally {
    op.end()
}
```

## 7. Logging outside an operation

Constructors and background setup have no active span. Use the observer's span-less logger:

```kotlin
class PaymentGateway(observers: ObserverFactory) {
    private val o11y = observers.named("payment_gateway")

    init {
        o11y.logger.info("gateway initialized")   // no span; just a named log line
    }
}
```

`o11y.logger` is also a normal `Logger` you can enrich: `o11y.logger.withValue("region", "us").warn("...")`.

## 8. Dependency injection

Everything takes plain constructors, so any DI framework works. Construct one `Observability` and
hand its `observers` (an `ObserverFactory`) to your components.

### Koin (`:observability-koin`)

```kotlin
val appModule = module {
    single { (get<Application>() as MyApp).observability }
    // observabilityModule binds Observability, its ObserverFactory, root Logger, TracerProvider,
    // and a by-name Observer lookup:
}

startKoin {
    androidContext(this@MyApp)
    modules(observabilityModule(observability), appModule)
}

// a component resolves its Observer by name:
class UserRepository : KoinComponent {
    private val o11y: Observer = get { parametersOf("user_repository") }
}

// or, with the provided scope helper:
val o11y = getKoin().observer("user_repository")
```

Most often you'll just inject the `ObserverFactory` and call `named(...)` yourself, which keeps the
component framework-agnostic:

```kotlin
single { UserRepository(observers = get(), api = get()) }
```

### Hilt

There's no Hilt artifact yet (Koin is the shipped option), but the plain constructors slot straight
into a module:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ObservabilityModule {
    @Provides @Singleton
    fun observability(@ApplicationContext ctx: Context): Observability =
        (ctx as MyApp).observability

    @Provides @Singleton
    fun observers(o: Observability): ObserverFactory = o.observers
}

class UserRepository @Inject constructor(
    observers: ObserverFactory,
    private val api: UserApi,
) {
    private val o11y = observers.named("user_repository")
}
```

## 9. Testing

Use `RecordingObserver` from `:observability-testing` in place of the real observer. It records every
value a unit attaches and on which pillar, with no live logger or tracer.

```kotlin
@Test
fun loadRecordsTheUserId() = runTest {
    val observer = RecordingObserver()
    val repo = UserRepository(observerFactoryOf(observer), fakeApi)

    repo.load("u-123")

    // assert against the recorded operation
    val op = observer.operations.single()
    assertTrue(op.ended)
    op.assertObserved(observedKeyValue(Keys.USER_ID, "u-123"))
    op.assertObserved(observedKey("user.tier").onSpan())   // reached the span
}
```

Matchers compose: `observedKey`, `observedKeyValue`, `observedValue`, each refinable with `.onSpan()`
or `.onLog()`. Cross-operation ordering is available via `observer.assertObservedInOrder(...)` and
`observer.assertObservedOperationWithValues(...)`.

`RecordingObserver` *is* an `Observer`, so inject it however your component takes one. If your
component wants an `ObserverFactory`, wrap it:

```kotlin
fun observerFactoryOf(observer: Observer): ObserverFactory = object : ObserverFactory {
    override val logger get() = observer.logger
    override fun named(name: String) = observer
}
```

When a unit just needs a *working* observer and you aren't asserting on it, use `noopObserver("name")`.

## 10. Configuration reference

The `Observability { }` block (see [`Config.kt`](../observability-api/src/main/kotlin/com/primandproper/platform/observability/Config.kt)):

| Field | Default | Notes |
|---|---|---|
| `serviceName` | — (required) | logger tag + `service.name` resource attribute |
| `logging.provider` | `LOGCAT` | `LOGCAT` or `NOOP` |
| `logging.level` | `INFO` | `DEBUG` / `INFO` / `WARN` / `ERROR` |
| `tracing.provider` | `OTEL` | `OTEL` or `NOOP` |
| `tracing.endpoint` | `""` | required for `OTEL`; OTLP collector URL |
| `tracing.sampleRatio` | `1.0` | head sampling in `[0, 1]`, parent-based |
| `tracing.useHttp` | `false` | OTLP/HTTP instead of OTLP/gRPC |

`validate()` runs inside the builder and throws `IllegalArgumentException` on missing/invalid values,
so a misconfigured stack fails loudly at startup rather than silently dropping telemetry.

Standard attribute keys live in [`Keys`](../observability-api/src/main/kotlin/com/primandproper/platform/observability/Keys.kt) —
prefer them over string literals so a value is named consistently wherever it's recorded.
