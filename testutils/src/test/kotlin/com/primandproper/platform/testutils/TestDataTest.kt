package com.primandproper.platform.testutils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of platform-go's `testutil_test.go`. */
class TestDataTest {
    @Test
    fun buildArbitraryImageReturnsCorrectDimensions() {
        val img = buildArbitraryImage(10)
        assertEquals(10, img.width)
        assertEquals(10, img.height)
    }

    @Test
    fun buildArbitraryImageHandlesSizeOne() {
        val img = buildArbitraryImage(1)
        assertEquals(1, img.width)
        assertEquals(1, img.height)
    }

    @Test
    fun buildArbitraryImageFillsTheDeterministicGradient() {
        val img = buildArbitraryImage(4)
        // Pixel (2, 3) is RGBA(2, 3, 5, 255) per the port's formula, stored as ARGB.
        val argb = img.getRGB(2, 3)
        assertEquals(0xFF, (argb ushr 24) and 0xFF)
        assertEquals(2, (argb ushr 16) and 0xFF)
        assertEquals(3, (argb ushr 8) and 0xFF)
        assertEquals(5, argb and 0xFF)
    }

    @Test
    fun buildArbitraryImagePngBytesReturnsValidPng() {
        val (img, data) = buildArbitraryImagePngBytes(5)
        assertEquals(5, img.width)
        assertTrue(data.isNotEmpty())
        // PNG magic bytes: 0x89 'P' 'N' 'G'.
        assertEquals(0x89.toByte(), data[0])
        assertEquals('P'.code.toByte(), data[1])
        assertEquals('N'.code.toByte(), data[2])
        assertEquals('G'.code.toByte(), data[3])
    }

    @Test
    fun buildTestRequestReturnsAnOptionsRequest() {
        val req = buildTestRequest()
        assertEquals("OPTIONS", req.method())
        assertEquals("whatever.whocares.gov", req.uri().host)
        assertEquals("https", req.uri().scheme)
    }

    @Test
    fun exampleKeysHaveTheirAdvertisedByteLengths() {
        assertEquals(32, EXAMPLE_32_BYTE_KEY.length)
        assertEquals(64, EXAMPLE_64_BYTE_KEY.length)
    }
}
