package com.primandproper.platform.authentication.tokens.mock

import com.primandproper.platform.authentication.tokens.Claims
import com.primandproper.platform.authentication.tokens.IssuedToken
import com.primandproper.platform.authentication.tokens.Issuer
import java.time.Instant
import kotlin.time.Duration

/**
 * A configurable [Issuer] test double, mirroring platform-go's moq-generated `tokens/mock.IssuerMock`.
 * Each method delegates to a settable `...Func`; calling a method whose `Func` was left `null` throws
 * [IllegalStateException], the same "unmocked call surfaces immediately" behavior moq's generated
 * panic gives. Every call's arguments are recorded in the matching `...Calls` list.
 *
 * Recording is guarded by a per-mock lock; the public accessors hand back an immutable snapshot, so a
 * recorder on one thread can't trip a reader iterating the calls on another.
 *
 * ```
 * val mock = IssuerMock(issueTokenFunc = { subject, _, _ -> IssuedToken("tok", "jti") })
 * ```
 */
public class IssuerMock(
    public var issueTokenFunc: (suspend (String, Duration, Map<String, Any?>) -> IssuedToken)? = null,
    public var parseTokenFunc: (suspend (String) -> Claims)? = null,
) : Issuer {
    private val lock = Any()

    private val _issueTokenCalls = mutableListOf<Triple<String, Duration, Map<String, Any?>>>()
    private val _parseTokenCalls = mutableListOf<String>()

    public val issueTokenCalls: List<Triple<String, Duration, Map<String, Any?>>>
        get() = synchronized(lock) { _issueTokenCalls.toList() }
    public val parseTokenCalls: List<String> get() = synchronized(lock) { _parseTokenCalls.toList() }

    override suspend fun issueToken(
        subject: String,
        expiry: Duration,
        extraClaims: Map<String, Any?>,
    ): IssuedToken {
        synchronized(lock) { _issueTokenCalls += Triple(subject, expiry, extraClaims) }
        return requireFunc(issueTokenFunc, "issueTokenFunc").invoke(subject, expiry, extraClaims)
    }

    override suspend fun parseToken(token: String): Claims {
        synchronized(lock) { _parseTokenCalls += token }
        return requireFunc(parseTokenFunc, "parseTokenFunc").invoke(token)
    }
}

/**
 * A configurable [Claims] test double, mirroring platform-go's moq-generated `tokens/mock.ClaimsMock`.
 * Follows the same "null `Func` throws, calls are recorded" contract as [IssuerMock]. The no-argument
 * accessors record only their arity, so their `...Calls` are plain invocation counters.
 */
public class ClaimsMock(
    public var subjectFunc: (() -> String)? = null,
    public var jtiFunc: (() -> String)? = null,
    public var expiresAtFunc: (() -> Instant?)? = null,
    public var getFunc: ((String) -> Any?)? = null,
    public var getStringOrNullFunc: ((String) -> String?)? = null,
) : Claims {
    private val lock = Any()

    private var _subjectCalls = 0
    private var _jtiCalls = 0
    private var _expiresAtCalls = 0
    private val _getCalls = mutableListOf<String>()
    private val _getStringOrNullCalls = mutableListOf<String>()

    public val subjectCalls: Int get() = synchronized(lock) { _subjectCalls }
    public val jtiCalls: Int get() = synchronized(lock) { _jtiCalls }
    public val expiresAtCalls: Int get() = synchronized(lock) { _expiresAtCalls }
    public val getCalls: List<String> get() = synchronized(lock) { _getCalls.toList() }
    public val getStringOrNullCalls: List<String> get() = synchronized(lock) { _getStringOrNullCalls.toList() }

    override fun subject(): String {
        synchronized(lock) { _subjectCalls++ }
        return requireFunc(subjectFunc, "subjectFunc").invoke()
    }

    override fun jti(): String {
        synchronized(lock) { _jtiCalls++ }
        return requireFunc(jtiFunc, "jtiFunc").invoke()
    }

    override fun expiresAt(): Instant? {
        synchronized(lock) { _expiresAtCalls++ }
        return requireFunc(expiresAtFunc, "expiresAtFunc").invoke()
    }

    override fun get(key: String): Any? {
        synchronized(lock) { _getCalls += key }
        return requireFunc(getFunc, "getFunc").invoke(key)
    }

    override fun getStringOrNull(key: String): String? {
        synchronized(lock) { _getStringOrNullCalls += key }
        return requireFunc(getStringOrNullFunc, "getStringOrNullFunc").invoke(key)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
