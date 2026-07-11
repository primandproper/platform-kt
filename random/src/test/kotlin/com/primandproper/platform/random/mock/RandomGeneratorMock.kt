package com.primandproper.platform.random.mock

import com.primandproper.platform.random.RandomGenerator

/**
 * A configurable [RandomGenerator] test double, mirroring platform-go's moq-generated
 * `random/mock.GeneratorMock`. Each method delegates to a settable `...Func`; calling a method whose
 * `Func` was left `null` throws [IllegalStateException], the same "unmocked call surfaces immediately"
 * behavior moq's generated panic gives. Every call's arguments are recorded in the matching
 * `...Calls` list, standing in for moq's generated `XCalls()` accessors.
 *
 * ```
 * val mock = RandomGeneratorMock(generateHexEncodedStringFunc = { length -> "a".repeat(length) })
 * ```
 */
public class RandomGeneratorMock(
    public var generateRawBytesFunc: ((Int) -> ByteArray)? = null,
    public var generateHexEncodedStringFunc: ((Int) -> String)? = null,
    public var generateBase32EncodedStringFunc: ((Int) -> String)? = null,
    public var generateBase64EncodedStringFunc: ((Int) -> String)? = null,
    public var generateAlphabetEncodedStringFunc: ((String, Int) -> String)? = null,
) : RandomGenerator {
    private val lock = Any()
    private val _generateRawBytesCalls = mutableListOf<Int>()
    private val _generateHexEncodedStringCalls = mutableListOf<Int>()
    private val _generateBase32EncodedStringCalls = mutableListOf<Int>()
    private val _generateBase64EncodedStringCalls = mutableListOf<Int>()
    private val _generateAlphabetEncodedStringCalls = mutableListOf<Pair<String, Int>>()

    public val generateRawBytesCalls: List<Int> get() = synchronized(lock) { _generateRawBytesCalls.toList() }
    public val generateHexEncodedStringCalls: List<Int> get() = synchronized(lock) { _generateHexEncodedStringCalls.toList() }
    public val generateBase32EncodedStringCalls: List<Int> get() = synchronized(lock) { _generateBase32EncodedStringCalls.toList() }
    public val generateBase64EncodedStringCalls: List<Int> get() = synchronized(lock) { _generateBase64EncodedStringCalls.toList() }
    public val generateAlphabetEncodedStringCalls: List<Pair<String, Int>>
        get() = synchronized(lock) { _generateAlphabetEncodedStringCalls.toList() }

    override fun generateRawBytes(length: Int): ByteArray {
        synchronized(lock) { _generateRawBytesCalls += length }
        return requireFunc(generateRawBytesFunc, "generateRawBytesFunc").invoke(length)
    }

    override fun generateHexEncodedString(length: Int): String {
        synchronized(lock) { _generateHexEncodedStringCalls += length }
        return requireFunc(generateHexEncodedStringFunc, "generateHexEncodedStringFunc").invoke(length)
    }

    override fun generateBase32EncodedString(length: Int): String {
        synchronized(lock) { _generateBase32EncodedStringCalls += length }
        return requireFunc(generateBase32EncodedStringFunc, "generateBase32EncodedStringFunc").invoke(length)
    }

    override fun generateBase64EncodedString(length: Int): String {
        synchronized(lock) { _generateBase64EncodedStringCalls += length }
        return requireFunc(generateBase64EncodedStringFunc, "generateBase64EncodedStringFunc").invoke(length)
    }

    override fun generateAlphabetEncodedString(
        alphabet: String,
        length: Int,
    ): String {
        synchronized(lock) { _generateAlphabetEncodedStringCalls += alphabet to length }
        return requireFunc(generateAlphabetEncodedStringFunc, "generateAlphabetEncodedStringFunc").invoke(alphabet, length)
    }

    private fun <F> requireFunc(
        func: F?,
        name: String,
    ): F = func ?: error("RandomGeneratorMock.$name: method is null but was just called")
}
