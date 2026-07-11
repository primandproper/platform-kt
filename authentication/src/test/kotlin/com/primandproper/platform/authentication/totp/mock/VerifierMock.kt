package com.primandproper.platform.authentication.totp.mock

import com.primandproper.platform.authentication.totp.Verifier

/**
 * A configurable [Verifier] test double, mirroring platform-go's moq-generated `totp/mock.VerifierMock`.
 * [verify] delegates to the settable [verifyFunc]; calling it while [verifyFunc] is `null` throws
 * [IllegalStateException], the same "unmocked call surfaces immediately" behavior moq's generated
 * panic gives. Every call's `(secret, code)` arguments are recorded in [verifyCalls].
 *
 * Recording is guarded by a per-mock lock; [verifyCalls] hands back an immutable snapshot, so a
 * recorder on one thread can't trip a reader iterating it on another.
 *
 * ```
 * val mock = VerifierMock(verifyFunc = { _, _ -> })
 * ```
 */
public class VerifierMock(
    public var verifyFunc: (suspend (String, String) -> Unit)? = null,
) : Verifier {
    private val lock = Any()

    private val _verifyCalls = mutableListOf<Pair<String, String>>()

    public val verifyCalls: List<Pair<String, String>> get() = synchronized(lock) { _verifyCalls.toList() }

    override suspend fun verify(
        secret: String,
        code: String,
    ) {
        synchronized(lock) { _verifyCalls += secret to code }
        val func = verifyFunc ?: error("mock.verifyFunc: method is null but was just called")
        func.invoke(secret, code)
    }
}
