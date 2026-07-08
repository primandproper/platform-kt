# platform-kt by example

A cookbook of short, real snippets — one per package — so you can see the shape of each API at a
glance. For the observability stack (the founding pillar) there's a dedicated task-oriented guide in
[USAGE.md](USAGE.md); everything else lives here.

Every snippet uses the actual public API. If you copy one and it doesn't resolve, the package moved —
open an issue rather than guessing.

## Conventions that hold across the whole tree

Learn these once and every package reads the same way:

- **Package root.** Everything is under `com.primandproper.platform.<area>` (e.g.
  `com.primandproper.platform.cache`). Backends live in a sub-package (`...cache.redis`).
- **Observability is opt-in, never required.** Factories that can emit telemetry take
  `logger: Logger? = null`, `tracerProvider: TracerProvider? = null`, or a bundled
  `observer: Observer` — all defaulting to no-ops. Omit them and nothing breaks; pass them and the
  call is traced and log-correlated. (`Logger`/`Tracer`/`Observer` come from
  `com.primandproper.platform.observability`.)
- **Circuit breakers are opt-in too.** Anything that calls a remote dependency accepts an optional
  `circuitBreaker: CircuitBreaker?` from `com.primandproper.platform.circuitbreaking`.
- **`suspend` where I/O happens.** Network/disk/lock calls are `suspend` — call them inside a
  coroutine (`runBlocking { }`, `runTest { }`, or another `suspend fun`). Pure/CPU helpers
  (hashers, `numbers`, `bitmask`, `compression`, `fake`, plain TOTP) are ordinary functions.
- **One module anatomy.** Each package is an `-api` module (interface + config DSL with `validate()`
  + `noop`/`mock` doubles) plus one or more backend modules. Depend on the backend you want; the
  `-api` types are what you program against.
- **Config DSLs fail loud.** Builders run `validate()` and throw `IllegalArgumentException` on bad
  input at construction time, not silently at first use.

`Duration` throughout is `kotlin.time.Duration` — `import kotlin.time.Duration.Companion.seconds`
(etc.).

---

## Tier 1 — foundation & networking spine

### `:identifiers` — sortable IDs

```kotlin
import com.primandproper.platform.identifiers.*

val id = newUlid()          // 26-char Crockford base32, time-ordered (the xid analog)
isValidUlid(id)             // true
val u = newUuid()           // UUIDv4
```

### `:errors` — sentinels, wrapping, HTTP/gRPC mapping

Errors are matched by **identity** through the wrap chain (Go's `errors.Is`), and map to transport
codes without leaking internals.

```kotlin
import com.primandproper.platform.errors.*
import com.primandproper.platform.errors.http.*

val err = wrap(ErrNoRows, "loading user u-123")   // PlatformException? (null if cause was null)

isError(err, ErrNoRows)                            // true — original sentinel found through the wrap
val mapping = toApiError(err)                      // HttpMapping(code = ErrDataNotFound, message = …)
val status = mapping.code.toHttpStatus()           // 404
```

### `:random` — crypto-secure strings

```kotlin
import com.primandproper.platform.random.*

val gen: RandomGenerator = SecureRandomGenerator()      // over SecureRandom
val token = gen.generateHexEncodedString(16)
val otp   = gen.generateAlphabetEncodedString("0123456789", length = 6)
val bytes = gen.generateRawBytes(32)
```

### `:retry` — coroutine-native backoff

```kotlin
import com.primandproper.platform.retry.*

val policy = ExponentialBackoffPolicy {
    maxAttempts = 5
    useJitter = true
    retryIf = { it !is IllegalArgumentException }
}
val user = policy.execute { api.fetchUser(id) }    // suspend; delays with delay()

// Flow variant:
import kotlinx.coroutines.flow.Flow
fun <T> Flow<T>.resilient(): Flow<T> = retryWithPolicy { maxAttempts = 3 }
```

### `:circuitbreaking` — trip on repeated failure

```kotlin
import com.primandproper.platform.circuitbreaking.*
import kotlin.time.Duration.Companion.seconds

val breaker = CircuitBreaker {
    failureThreshold = 5
    resetTimeout = 10.seconds
}
val resp = breaker.execute { flakyDependency() }   // suspend; throws ErrCircuitBroken while OPEN
breaker.state.value                                 // CircuitState.CLOSED / OPEN / HALF_OPEN

// One breaker per key (per-tenant, per-host, …):
import com.primandproper.platform.circuitbreaking.partitioned.*
val keyed = KeyedCircuitBreaker(KeyedCircuitBreakerConfig().apply {
    keys += listOf("tenant-a", "tenant-b")
    base { failureThreshold = 5 }
})
keyed["tenant-a"].execute { call() }
```

### `:httpclient-*` — one contract, two engines

Traced HTTP with header redaction built in. OkHttp for client/Android, Ktor/CIO for server.

```kotlin
import com.primandproper.platform.httpclient.*
import com.primandproper.platform.httpclient.okhttp.OkHttpHttpClient   // or ...ktor.KtorHttpClient
import kotlin.time.Duration.Companion.seconds

val http: HttpClient = OkHttpHttpClient.create(HttpClientConfig { timeout = 5.seconds })

val resp = http.execute(HttpRequest.build {         // suspend
    method = HttpMethod.POST
    url = "https://api.example.com/things"
    header("Content-Type", "application/json")
    body("""{"name":"widget"}""")
})
if (resp.isSuccessful) println(resp.bodyAsText())
http.close()
```

> The concrete clients are built via `create(...)` (the constructors are `internal`). To actually
> emit spans, pass a real `io.opentelemetry.api.OpenTelemetry` as the second `create(...)` argument —
> it defaults to `OpenTelemetry.noop()`. `Authorization`/`Cookie`/… headers are redacted before any
> request data reaches a span.

---

## Tier 2 — core cross-surface services

### `:cache-*` — typed cache, in-memory or Redis

`get` returns `T?` (null is a miss). `InMemoryCache` and `RedisCache` are both `BatchCache`.

```kotlin
import com.primandproper.platform.cache.*

val cache: BatchCache<String> = InMemoryCache()
cache.set("greeting", "hello")
val v: String? = cache.get("greeting")

// Redis-backed via the unifying factory:
import kotlin.time.Duration.Companion.hours
val redis = provideCache(
    config = CacheConfig(CacheProvider.REDIS, expiry = 1.hours),
    codec = StringCacheCodec,                                   // your T <-> String codec
    redisConfig = RedisCacheConfig(queueAddresses = listOf("localhost:6379")),
)
```

### `:cryptography-*` — AEAD encryption + hashing

```kotlin
import com.primandproper.platform.cryptography.encryption.aes.newAesEncryptorDecryptor
import com.primandproper.platform.cryptography.hashing.sha256.newSHA256Hasher

val aead = newAesEncryptorDecryptor(key = ByteArray(32) { /* your 32-byte key */ 0 })
val sealed = aead.encrypt("4111-1111-1111-1111")   // suspend; base64url(nonce||ciphertext||tag)
val plain  = aead.decrypt(sealed)                   // suspend

val digest = newSHA256Hasher().hash("payload")      // non-suspend; lowercase hex
```

### `:secrets-*` — read secrets from the environment

```kotlin
import com.primandproper.platform.secrets.env.EnvSecretSource

val secrets = EnvSecretSource()
val token = secrets.getSecret("API_TOKEN")   // suspend; throws SecretNotFoundException if unset.
                                             // The value never lands on a span or log line.
```

### `:featureflags-*` — evaluate flags

The boolean check is `canUseFeature` (returns `false` on any error). All evals are `suspend`.

```kotlin
import com.primandproper.platform.featureflags.*

val flags = InMemoryFeatureFlagManager(mapOf("new-checkout" to true, "max-items" to 42L))

val ctx = EvaluationContext(targetingKey = "user-123", attributes = mapOf("plan" to "pro"))
if (flags.canUseFeature("new-checkout", ctx)) { /* … */ }
val maxItems = flags.getInt64Value("max-items", defaultValue = 10L, ctx)
```

Swap `InMemoryFeatureFlagManager` for `launchDarklyFeatureFlagManager(LaunchDarklyConfig(sdkKey = …))`
from `:featureflags-launchdarkly` in production — same interface.

### `:analytics-*` — identify users, track events

`addUser` = identify, `eventOccurred` = track. Both `suspend`.

```kotlin
import com.primandproper.platform.analytics.segment.SegmentEventReporter

val reporter = SegmentEventReporter(apiToken = "write-key")
reporter.addUser("user-123", mapOf("plan" to "pro"))
reporter.eventOccurred("checkout_completed", "user-123", mapOf("total" to 42))
reporter.close()
```

---

## Tier 3 — server platform

### `:server-*` + `:routing-*` — an HTTP service

```kotlin
import com.primandproper.platform.routing.*
import com.primandproper.platform.routing.ktor.provideRouter
import com.primandproper.platform.server.*
import com.primandproper.platform.server.http.provideHttpServer
import kotlin.time.Duration.Companion.seconds

val router = provideRouter(RoutingConfig(RoutingProvider.KTOR, RouterSettings(serviceName = "svc")))

router.get("/health") { call -> call.respondText(200, "ok") }
router.get("/things/{id}") { call ->
    call.respondText(200, "thing ${call.pathParameter("id")}")
}
router.route("/api") { sub ->
    sub.get("/ping") { call -> call.respondText(200, "pong") }
}

val server = provideHttpServer(
    HttpServerConfig(port = 8080, startupDeadline = 5.seconds),
    router,
    serviceName = "svc",
)
server.serve()          // suspend; returns once bound (non-blocking)
// server.shutdown()    // graceful drain
```

### `:database-*` — Postgres via Exposed

```kotlin
import com.primandproper.platform.database.config.*
import com.primandproper.platform.database.postgres.provideDatabaseClient

val db = provideDatabaseClient(
    DatabaseConfig(
        provider = DatabaseProviders.POSTGRES,
        readConnection = ConnectionDetails(
            host = "localhost", port = 5432,
            username = "app", password = "secret", database = "app",
        ),
    ),
)
db.use { client ->
    // Query through client.writeDataSource / client.readDataSource (JDBC or Exposed).
    client.currentTime()
}
```

### `:ratelimiting-*` — token bucket

```kotlin
import com.primandproper.platform.ratelimiting.InMemoryRateLimiter

val limiter = InMemoryRateLimiter(requestsPerSec = 10.0, burstSize = 20)
if (limiter.allow(clientIp)) handle() else reject429()   // suspend; true = within limit
```

For a shared limit across instances, use `provideRateLimiter(RateLimitingConfig(provider = REDIS), …)`
from `:ratelimiting-redis`.

### `:messagequeue-*` — publish / subscribe

```kotlin
import com.primandproper.platform.messagequeue.*
import com.primandproper.platform.messagequeue.redis.*

val publishers = providePublisherProvider(
    MessageQueueProvider.REDIS,
    RedisMessageQueueConfig(queueAddresses = listOf("localhost:6379")),
)
val orders = publishers.providePublisher("orders")
orders.publish("order-created")                          // suspend

// Consumer side:
val consumers = provideConsumerProvider(
    MessageQueueProvider.REDIS,
    RedisMessageQueueConfig(queueAddresses = listOf("localhost:6379")),
)
val consumer = consumers.provideConsumer("orders") { bytes -> handle(bytes) }
consumer.consume()   // suspend; runs until the coroutine is cancelled
```

### `:distributedlock-*` — mutual exclusion with a TTL

```kotlin
import com.primandproper.platform.distributedlock.*
import kotlin.time.Duration.Companion.seconds

val locker = MemoryLocker()                              // or RedisLocker / PostgresLocker
val lock = locker.acquire("account:42", ttl = 30.seconds)   // throws ErrLockNotAcquired if held
try {
    // critical section
} finally {
    lock.release()
}
```

### `:email-*` — transactional email

```kotlin
import com.primandproper.platform.email.*
import com.primandproper.platform.email.resend.ResendEmailer
import com.primandproper.platform.httpclient.okhttp.OkHttpHttpClient

val emailer = ResendEmailer(apiToken = "re_…", httpClient = OkHttpHttpClient.create())
emailer.sendEmail(OutboundEmailMessage(
    toAddress = "user@example.com",
    fromAddress = "no-reply@myapp.com",
    subject = "Welcome",
    htmlContent = "<p>Hello!</p>",
))
```

### `:uploads-*` — blob storage

```kotlin
import com.primandproper.platform.uploads.*
import com.primandproper.platform.uploads.objectstorage.MemoryBucket   // or S3, filesystem

val uploads = Uploader(MemoryBucket(), bucketName = "avatars")
uploads.saveBytes("u-123/avatar.png", pngBytes)          // suspend
val bytes = uploads.readBytes("u-123/avatar.png")        // suspend
```

### `:eventstream-*` — server-sent events

```kotlin
import com.primandproper.platform.eventstream.*
import com.primandproper.platform.eventstream.ktor.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.streaming(observer: Observer) {
    installEventStreamSse()
    routing {
        sseEventStream("/events", observer) { stream ->
            stream.send(Event(type = "tick", payload = """{"n":1}"""))   // suspend
        }
    }
}
```

(On Android, `:eventstream-android` flips the direction — it *consumes* an SSE/WS stream into a `Flow`.)

---

## Tier 4 — domain, AI & utilities

### `:llm-*` — chat completions

Defaults to the current Claude models; the API key is redacted from telemetry.

```kotlin
import com.primandproper.platform.llm.*
import com.primandproper.platform.llm.anthropic.*
import com.primandproper.platform.httpclient.okhttp.OkHttpHttpClient

val llm = AnthropicLlmProvider(
    apiKey = System.getenv("ANTHROPIC_API_KEY"),
    httpClient = OkHttpHttpClient.create(),
)
val result = llm.complete(CompletionParams(
    model = AnthropicModels.OPUS_4_8,        // "claude-opus-4-8" (also the default)
    messages = listOf(Message(Role.USER, "Summarize the CAP theorem in one sentence.")),
))
println(result.content)                       // suspend
```

### `:embeddings-*` — vector embeddings

```kotlin
import com.primandproper.platform.embeddings.*
import com.primandproper.platform.embeddings.openai.OpenAiEmbedder
import com.primandproper.platform.httpclient.okhttp.OkHttpHttpClient

val embedder = OpenAiEmbedder(
    apiKey = System.getenv("OPENAI_API_KEY"),
    httpClient = OkHttpHttpClient.create(),
)
val embedding = embedder.generateEmbedding(EmbeddingInput(content = "hello world"))   // suspend
println(embedding.vector.size)                // 1536 for text-embedding-3-small
```

### `:authentication` — passwords, JWTs, TOTP

```kotlin
import com.primandproper.platform.authentication.argon2.newArgon2Authenticator

val auth = newArgon2Authenticator()
val hash = auth.hashPassword("s3cret")               // suspend; Argon2id
val ok   = auth.passwordMatches(hash, "s3cret")      // suspend; true
```

```kotlin
import com.primandproper.platform.authentication.tokens.jwt.newJwtSigner
import kotlin.time.Duration.Companion.minutes

val signer = newJwtSigner(issuer = "myapp", audience = "myapp-web", signingKey = key /* ByteArray */)
val issued = signer.issueToken(subject = "user-123", expiry = 15.minutes)   // suspend; HS256
val claims = signer.parseToken(issued.token)                                 // suspend; validates
val subject = claims.subject()
```

```kotlin
import com.primandproper.platform.authentication.totp.*
import java.time.Instant

val code  = generateTotpCode(secret = base32Secret, time = Instant.now())    // RFC 6238, 6-digit
val valid = validateTotpCode(code, base32Secret, Instant.now())              // Boolean
val uri   = totpProvisioningUri(issuer = "MyApp", accountName = "user@example.com", secret = base32Secret)
```

### `:notifications-*` — push notifications

```kotlin
import com.primandproper.platform.notifications.*
import com.primandproper.platform.notifications.fcm.FcmPushSender
import com.primandproper.platform.httpclient.okhttp.OkHttpHttpClient

val push = FcmPushSender(
    projectId = "my-project",
    httpClient = OkHttpHttpClient.create(),
    tokenProvider = { fetchOAuthAccessToken() },     // suspend () -> String
)
push.sendPush(PLATFORM_ANDROID, deviceToken, PushMessage(title = "Hi", body = "New message"))
```

### `:qrcodes` — a scannable 2FA QR

```kotlin
import com.primandproper.platform.qrcodes.DefaultQrCodeBuilder

val qr = DefaultQrCodeBuilder(issuer = "MyApp")
val dataUri = qr.buildQrCode(username = "user@example.com", twoFactorSecret = base32Secret)
// "data:image/png;base64,…" — it builds the otpauth:// URI for you. Pairs with :authentication TOTP.
```

### `:compression` — zstd / S2

```kotlin
import com.primandproper.platform.compression.*

val zstd = newCompressor(Algorithm.ZSTD)
val packed = zstd.compressBytes(payload)             // non-suspend
val original = zstd.decompressBytes(packed)          // guards against decompression bombs
```

### `:numbers` — decimal rounding & scaling

```kotlin
import com.primandproper.platform.numbers.*

roundToDecimalPlaces(3.14159f, precision = 2)        // 3.14 (HALF_UP)
scale(2.5f, factor = 2.0f)                            // 5.0
```

### `:bitmask` — width-typed flag sets

```kotlin
import com.primandproper.platform.bitmask.Bitmask

val READ = 1uL; val WRITE = 2uL; val ADMIN = 4uL
val perms = Bitmask.of(width = 8, READ, WRITE)       // immutable; ops return new instances
perms.has(READ)                                       // true
perms.hasAll(READ, ADMIN)                             // false
val elevated = perms.set(ADMIN)
```

### `:fake` — deterministic test data

```kotlin
import com.primandproper.platform.fake.Fake

data class Person(val fullName: String, val email: String, val age: Int)

val person = Fake(seed = 1234L).mustBuildFake<Person>()   // reproducible for a given seed
```

---

Missing a package? The full catalog is in the [README](../README.md#module-catalog); the
authoritative per-package status (backends shipped, seams left open) is in
[PORTING_STATUS.md](PORTING_STATUS.md).
