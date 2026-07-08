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
 * ```
 * val mock = IssuerMock(issueTokenFunc = { subject, _, _ -> IssuedToken("tok", "jti") })
 * ```
 */
public class IssuerMock(
    public var issueTokenFunc: (suspend (String, Duration, Map<String, Any?>) -> IssuedToken)? = null,
    public var parseTokenFunc: (suspend (String) -> Claims)? = null,
) : Issuer {
    public val issueTokenCalls: MutableList<Triple<String, Duration, Map<String, Any?>>> = mutableListOf()
    public val parseTokenCalls: MutableList<String> = mutableListOf()

    override suspend fun issueToken(
        subject: String,
        expiry: Duration,
        extraClaims: Map<String, Any?>,
    ): IssuedToken {
        issueTokenCalls += Triple(subject, expiry, extraClaims)
        return requireFunc(issueTokenFunc, "issueTokenFunc").invoke(subject, expiry, extraClaims)
    }

    override suspend fun parseToken(token: String): Claims {
        parseTokenCalls += token
        return requireFunc(parseTokenFunc, "parseTokenFunc").invoke(token)
    }
}

/**
 * A configurable [Claims] test double, mirroring platform-go's moq-generated `tokens/mock.ClaimsMock`.
 * Follows the same "null `Func` throws, calls are recorded" contract as [IssuerMock].
 */
public class ClaimsMock(
    public var subjectFunc: (() -> String)? = null,
    public var jtiFunc: (() -> String)? = null,
    public var expiresAtFunc: (() -> Instant?)? = null,
    public var getFunc: ((String) -> Pair<Any?, Boolean>)? = null,
    public var getStringFunc: ((String) -> Pair<String, Boolean>)? = null,
) : Claims {
    public val subjectCalls: MutableList<Unit> = mutableListOf()
    public val jtiCalls: MutableList<Unit> = mutableListOf()
    public val expiresAtCalls: MutableList<Unit> = mutableListOf()
    public val getCalls: MutableList<String> = mutableListOf()
    public val getStringCalls: MutableList<String> = mutableListOf()

    override fun subject(): String {
        subjectCalls += Unit
        return requireFunc(subjectFunc, "subjectFunc").invoke()
    }

    override fun jti(): String {
        jtiCalls += Unit
        return requireFunc(jtiFunc, "jtiFunc").invoke()
    }

    override fun expiresAt(): Instant? {
        expiresAtCalls += Unit
        return requireFunc(expiresAtFunc, "expiresAtFunc").invoke()
    }

    override fun get(key: String): Pair<Any?, Boolean> {
        getCalls += key
        return requireFunc(getFunc, "getFunc").invoke(key)
    }

    override fun getString(key: String): Pair<String, Boolean> {
        getStringCalls += key
        return requireFunc(getStringFunc, "getStringFunc").invoke(key)
    }
}

private fun <F> requireFunc(
    func: F?,
    name: String,
): F = func ?: error("mock.$name: method is null but was just called")
