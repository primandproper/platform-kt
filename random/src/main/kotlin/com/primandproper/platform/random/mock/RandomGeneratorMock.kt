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
    public val generateRawBytesCalls: MutableList<Int> = mutableListOf()
    public val generateHexEncodedStringCalls: MutableList<Int> = mutableListOf()
    public val generateBase32EncodedStringCalls: MutableList<Int> = mutableListOf()
    public val generateBase64EncodedStringCalls: MutableList<Int> = mutableListOf()
    public val generateAlphabetEncodedStringCalls: MutableList<Pair<String, Int>> = mutableListOf()

    override fun generateRawBytes(length: Int): ByteArray {
        generateRawBytesCalls += length
        return requireFunc(generateRawBytesFunc, "generateRawBytesFunc").invoke(length)
    }

    override fun generateHexEncodedString(length: Int): String {
        generateHexEncodedStringCalls += length
        return requireFunc(generateHexEncodedStringFunc, "generateHexEncodedStringFunc").invoke(length)
    }

    override fun generateBase32EncodedString(length: Int): String {
        generateBase32EncodedStringCalls += length
        return requireFunc(generateBase32EncodedStringFunc, "generateBase32EncodedStringFunc").invoke(length)
    }

    override fun generateBase64EncodedString(length: Int): String {
        generateBase64EncodedStringCalls += length
        return requireFunc(generateBase64EncodedStringFunc, "generateBase64EncodedStringFunc").invoke(length)
    }

    override fun generateAlphabetEncodedString(
        alphabet: String,
        length: Int,
    ): String {
        generateAlphabetEncodedStringCalls += alphabet to length
        return requireFunc(generateAlphabetEncodedStringFunc, "generateAlphabetEncodedStringFunc").invoke(alphabet, length)
    }

    private fun <F> requireFunc(
        func: F?,
        name: String,
    ): F = func ?: error("RandomGeneratorMock.$name: method is null but was just called")
}
