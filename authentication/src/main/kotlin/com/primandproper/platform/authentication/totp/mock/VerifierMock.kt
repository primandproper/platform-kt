package com.primandproper.platform.authentication.totp.mock

import com.primandproper.platform.authentication.totp.Verifier

/**
 * A configurable [Verifier] test double, mirroring platform-go's moq-generated `totp/mock.VerifierMock`.
 * [verify] delegates to the settable [verifyFunc]; calling it while [verifyFunc] is `null` throws
 * [IllegalStateException], the same "unmocked call surfaces immediately" behavior moq's generated
 * panic gives. Every call's `(secret, code)` arguments are recorded in [verifyCalls].
 *
 * ```
 * val mock = VerifierMock(verifyFunc = { _, _ -> })
 * ```
 */
public class VerifierMock(
    public var verifyFunc: (suspend (String, String) -> Unit)? = null,
) : Verifier {
    public val verifyCalls: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun verify(
        secret: String,
        code: String,
    ) {
        verifyCalls += secret to code
        val func = verifyFunc ?: error("mock.verifyFunc: method is null but was just called")
        func.invoke(secret, code)
    }
}
