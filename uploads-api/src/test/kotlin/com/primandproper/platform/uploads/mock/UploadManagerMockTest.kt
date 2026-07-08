package com.primandproper.platform.uploads.mock

import com.primandproper.platform.uploads.SaveOptions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Mirrors the moq-generated `mockuploads` contract: null `Func` throws, calls are recorded. */
class UploadManagerMockTest {
    @Test
    fun `configured funcs run and calls are recorded`() =
        runTest {
            val mock =
                UploadManagerMock(
                    saveFunc = { _, _, _ -> },
                    existsFunc = { path -> path == "known" },
                )
            mock.save("k", ByteArrayInputStream(ByteArray(0)), SaveOptions(contentType = "text/plain"))
            assertTrue(mock.exists("known"))

            assertEquals("k", mock.saveCalls.single().first)
            assertEquals("text/plain", mock.saveCalls.single().third.contentType)
            assertEquals(listOf("known"), mock.existsCalls)
        }

    @Test
    fun `calling an unset func throws`() =
        runTest {
            val mock = UploadManagerMock()
            assertFailsWith<IllegalStateException> { mock.open("k") }
        }

    @Test
    fun `capability mocks record and delegate`() =
        runTest {
            val signer = UrlSignerMock(signedUrlFunc = { path, _ -> "https://signed/$path" })
            assertEquals("https://signed/k", signer.signedUrl("k"))
            assertEquals("k", signer.signedUrlCalls.single().first)

            val lister = ListerMock(listFunc = { flowOf() })
            assertEquals(emptyList(), lister.list("p").toList())
            assertEquals(listOf("p"), lister.listCalls)
        }
}
