package com.primandproper.platform.circuitbreaking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A scriptable, recording [CircuitBreaker] test double — the idiomatic-Kotlin analog of the
 * moq-generated `circuitbreaking/mock.CircuitBreakerMock`. Where the Go mock lets a test supply a
 * closure per method and inspect the recorded call lists, this records call counts and lets a test
 * force the outcome via [reject].
 *
 * With `reject = true` every [execute] throws [CircuitBrokenException] without running the block (a breaker
 * pinned open), which is how the partitioned tests assert per-key isolation. With the default
 * `reject = false` it passes calls through, counting successes and failures so a test can assert how
 * a collaborator exercised the breaker.
 */
public class RecordingCircuitBreaker(
    reject: Boolean = false,
    initialState: CircuitState = if (reject) CircuitState.OPEN else CircuitState.CLOSED,
) : CircuitBreaker {
    private val reject: Boolean = reject
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<CircuitState> = _state.asStateFlow()

    /** Total calls to [execute], including rejected ones. */
    public var executeCount: Int = 0
        private set

    /** Calls whose block returned normally. */
    public var successCount: Int = 0
        private set

    /** Calls whose block threw (excluding [CancellationException]). */
    public var failureCount: Int = 0
        private set

    /** Calls rejected with [CircuitBrokenException] because [reject] is set. */
    public var rejectionCount: Int = 0
        private set

    override suspend fun <T> execute(block: suspend () -> T): T {
        executeCount++
        if (reject) {
            rejectionCount++
            throw CircuitBrokenException()
        }
        return try {
            val result = block()
            successCount++
            result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            failureCount++
            throw error
        }
    }
}
