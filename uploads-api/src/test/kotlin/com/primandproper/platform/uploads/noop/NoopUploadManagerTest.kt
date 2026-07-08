package com.primandproper.platform.uploads.noop

import com.primandproper.platform.uploads.Attributer
import com.primandproper.platform.uploads.Lister
import com.primandproper.platform.uploads.RangeReader
import com.primandproper.platform.uploads.UrlSigner
import com.primandproper.platform.uploads.listAll
import com.primandproper.platform.uploads.readBytes
import com.primandproper.platform.uploads.saveBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of platform-go's `uploads/noop.TestUploadManager` — every op is a safe no-op. */
class NoopUploadManagerTest {
    private val noop = NoopUploadManager()

    @Test
    fun `save drains the source and open returns empty`() =
        runTest {
            noop.saveBytes("k", "ignored".toByteArray())
            assertEquals(0, noop.readBytes("k").size)
        }

    @Test
    fun `exists is always false and delete is a no-op`() =
        runTest {
            assertFalse(noop.exists("anything"))
            noop.delete("anything")
        }

    @Test
    fun `capabilities are satisfied and inert`() =
        runTest {
            assertEquals(0, (noop as RangeReader).openRange("k", 0, 10).readBytes().size)
            assertTrue((noop as Attributer).attributes("k").contentType == null)
            assertEquals(emptyList(), listAll(noop as Lister, ""))
            assertEquals("", (noop as UrlSigner).signedUrl("k"))
        }
}
