package com.primandproper.platform.server

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the ported `server/grpc.Config` on the deferred gRPC seam. */
class GrpcServerConfigTest {
    @Test
    fun `port must be in range`() {
        assertFailsWith<IllegalArgumentException> { GrpcServerConfig(port = 0).validate() }
    }

    @Test
    fun `tlsEnabled requires both PEM paths`() {
        assertFalse(GrpcServerConfig(port = 9000, tlsCertificateFile = "cert").tlsEnabled)
        assertTrue(
            GrpcServerConfig(port = 9000, tlsCertificateFile = "cert", tlsCertificateKeyFile = "key").tlsEnabled,
        )
    }
}
