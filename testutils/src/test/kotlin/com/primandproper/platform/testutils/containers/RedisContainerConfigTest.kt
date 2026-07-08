package com.primandproper.platform.testutils.containers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of the pure (no-Docker) logic in platform-go's `testutils/containers/redistest`. */
class RedisContainerConfigTest {
    @Test
    fun defaultConfigUsesTheDefaultImageAndNoCluster() {
        val cfg = RedisContainerConfig()
        assertEquals(DEFAULT_REDIS_IMAGE, cfg.image)
        assertFalse(cfg.clusterEnabled)
    }

    @Test
    fun optionBlockMergesOverrides() {
        val cfg =
            RedisContainerConfig().apply {
                image = "docker.io/redis:8"
                clusterEnabled = true
            }
        assertEquals("docker.io/redis:8", cfg.image)
        assertTrue(cfg.clusterEnabled)
    }

    @Test
    fun redisCommandIsEmptyWhenClusterDisabled() {
        assertTrue(redisCommand(RedisContainerConfig()).isEmpty())
    }

    @Test
    fun redisCommandAddsClusterFlagWhenEnabled() {
        val command = redisCommand(RedisContainerConfig().apply { clusterEnabled = true })
        assertEquals(listOf("redis-server", "--cluster-enabled", "yes"), command)
    }

    @Test
    fun redisAddressAssemblesHostAndPort() {
        assertEquals("localhost:6379", redisAddress("localhost", REDIS_PORT))
        assertEquals("127.0.0.1:49153", redisAddress("127.0.0.1", 49153))
    }

    @Test
    fun stripRedisSchemeTrimsOnlyTheScheme() {
        assertEquals("localhost:6379", stripRedisScheme("redis://localhost:6379"))
        // No scheme present: left untouched.
        assertEquals("localhost:6379", stripRedisScheme("localhost:6379"))
    }
}
