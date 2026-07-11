package com.primandproper.platform.observability.testing

import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracer
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.Operation
import com.primandproper.platform.observability.Span
import com.primandproper.platform.observability.Tracer
import java.util.concurrent.atomic.AtomicInteger

/** Which pillar(s) an observation reached. Port of platform-go's `Pillar`. */
public enum class Pillar { BOTH, SPAN, LOG }

/** A single recorded key/value, tagged with the pillar(s) it hit and a global sequence number. */
public data class Observation(
    val seq: Int,
    val key: String,
    val value: Any?,
    val pillar: Pillar,
)

/**
 * An [Observer] that records every value units attach, so a test can assert which fields a unit
 * observed and on which pillar — without a live logger or tracer. Drop-in for the production
 * [Observer]; structurally identical so no test setup is needed beyond construction.
 *
 * Thread-safe: a shared monotonic sequence orders observations across concurrent operations,
 * mirroring the Go `RecordingObserver`.
 */
public class RecordingObserver : Observer {
    private val seq = AtomicInteger(0)
    private val lock = Any()
    private val _operations = mutableListOf<RecordingOperation>()

    public val operations: List<RecordingOperation>
        get() = synchronized(lock) { _operations.toList() }

    override val logger: Logger = NoopLogger
    override val tracer: Tracer = NoopTracer

    override fun begin(name: String): Operation {
        val op = RecordingOperation(this, name)
        synchronized(lock) { _operations += op }
        return op
    }

    internal fun nextSeq(): Int = seq.getAndIncrement()

    /** All observations across all operations, globally ordered by sequence. */
    public fun stream(): List<Observation> = operations.flatMap { it.observations }.sortedBy { it.seq }

    /** Asserts the matchers occur, in order, somewhere in the global stream. */
    public fun assertObservedInOrder(vararg matchers: Matcher) {
        assertInOrder(stream(), matchers.toList()) { "recording observer" }
    }

    /** Finds the (ended) operation that observed all of [pairs], or fails. */
    public fun assertObservedOperationWithValues(vararg pairs: Pair<String, Any?>) {
        val wanted = pairs.toMap()
        val match =
            operations.firstOrNull { op ->
                wanted.all { (k, v) -> op.values[k] == v }
            } ?: throw AssertionError(
                "no operation observed all of $wanted; saw ${operations.map { it.values }}",
            )
        if (!match.ended) throw AssertionError("operation \"${match.name}\" matched but was never ended")
    }
}

/**
 * The [Operation] handed back by [RecordingObserver]. Records instead of executing.
 *
 * Thread-safe: every mutation and every public read holds the per-operation [lock], and the collection
 * properties hand back an immutable snapshot rather than the live backing collection. So a recorder
 * calling [set] on one thread can't throw [ConcurrentModificationException] against a reader iterating
 * [observations] (or [RecordingObserver.stream]) on another — the failure mode this double previously
 * had despite advertising thread safety.
 */
public class RecordingOperation internal constructor(
    private val owner: RecordingObserver,
    public val name: String,
) : Operation {
    private val lock = Any()
    private val _observations = mutableListOf<Observation>()
    private val _values = mutableMapOf<String, Any?>() // Set (BOTH)
    private val _spanValues = mutableMapOf<String, Any?>() // Set + SpanOnly
    private val _logValues = mutableMapOf<String, Any?>() // Set + LogOnly
    private val _errors = mutableListOf<Throwable>()

    /** Snapshot of the observations recorded so far; safe to iterate while other threads keep recording. */
    public val observations: List<Observation> get() = synchronized(lock) { _observations.toList() }
    public val values: Map<String, Any?> get() = synchronized(lock) { _values.toMap() }
    public val spanValues: Map<String, Any?> get() = synchronized(lock) { _spanValues.toMap() }
    public val logValues: Map<String, Any?> get() = synchronized(lock) { _logValues.toMap() }
    public val errors: List<Throwable> get() = synchronized(lock) { _errors.toList() }

    @Volatile
    public var ended: Boolean = false
        private set

    override val logger: Logger = NoopLogger
    override val span: Span = io.opentelemetry.api.trace.Span.getInvalid()

    override fun set(
        key: String,
        value: Any?,
    ): Operation =
        apply {
            synchronized(lock) {
                appendObservation(key, value, Pillar.BOTH)
                _values[key] = value
                _spanValues[key] = value
                _logValues[key] = value
            }
        }

    override fun set(vararg pairs: Pair<String, Any?>): Operation {
        for ((k, v) in pairs) set(k, v)
        return this
    }

    override fun spanOnly(
        key: String,
        value: Any?,
    ): Operation =
        apply {
            synchronized(lock) {
                appendObservation(key, value, Pillar.SPAN)
                _spanValues[key] = value
            }
        }

    override fun logOnly(
        key: String,
        value: Any?,
    ): Operation =
        apply {
            synchronized(lock) {
                appendObservation(key, value, Pillar.LOG)
                _logValues[key] = value
            }
        }

    override fun <E : Throwable> error(
        err: E,
        message: String,
    ): E {
        synchronized(lock) { _errors += err }
        return err
    }

    override fun acknowledge(
        err: Throwable,
        message: String,
    ) {
        synchronized(lock) { _errors += err }
    }

    override fun makeCurrent(): AutoCloseable = AutoCloseable {}

    override fun end() {
        ended = true
    }

    /** Appends an observation. Callers must already hold [lock]. */
    private fun appendObservation(
        key: String,
        value: Any?,
        pillar: Pillar,
    ) {
        _observations += Observation(owner.nextSeq(), key, value, pillar)
    }

    /** Asserts the matchers all occur somewhere in this operation (any order). */
    public fun assertObserved(vararg matchers: Matcher) {
        val snapshot = observations
        for (m in matchers) {
            if (snapshot.none { m.matches(it) }) {
                throw AssertionError("operation \"$name\" did not observe ${m.desc}; saw $snapshot")
            }
        }
    }

    /** Asserts the matchers occur, in order, within this operation. */
    public fun assertObservedInOrder(vararg matchers: Matcher) {
        assertInOrder(observations, matchers.toList()) { "operation \"$name\"" }
    }
}

private fun assertInOrder(
    stream: List<Observation>,
    matchers: List<Matcher>,
    owner: () -> String,
) {
    var i = 0
    for (o in stream) {
        if (i < matchers.size && matchers[i].matches(o)) i++
    }
    if (i < matchers.size) {
        throw AssertionError("${owner()} did not observe, in order, ${matchers[i].desc}; saw $stream")
    }
}
