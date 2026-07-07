package com.primandproper.platform.featureflags.launchdarkly

import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.server.Components
import com.launchdarkly.sdk.server.LDClient
import com.launchdarkly.sdk.server.LDConfig
import com.launchdarkly.sdk.server.integrations.TestData
import com.primandproper.platform.featureflags.EvaluationContext
import com.primandproper.platform.featureflags.FlagAttributes
import com.primandproper.platform.featureflags.config.LaunchDarklyConfig
import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Port of platform-go's `featureflags/launchdarkly/feature_flag_manager_test.go`. Uses the SDK's
 * offline `TestData` data source so the whole matrix (bool/string/int/float/object, plus
 * default-on-miss) exercises the real client and the platform adaptation logic without a network
 * connection. Circuit-breaker-specific cases are dropped: that subsystem is not part of this port.
 */
class LaunchDarklyFeatureFlagManagerTest {
    private val client: LDClient
    private val manager: LaunchDarklyFeatureFlagManager
    private val observer = RecordingObserver()

    init {
        val td = TestData.dataSource()
        td.update(td.flag("bool-flag").valueForAll(LDValue.of(true)))
        td.update(td.flag("string-flag").valueForAll(LDValue.of("hello-world")))
        td.update(td.flag("int-flag").valueForAll(LDValue.of(42)))
        td.update(td.flag("float-flag").valueForAll(LDValue.of(3.14)))
        td.update(td.flag("object-flag").valueForAll(LDValue.buildObject().put("key", "value").build()))

        val config =
            LDConfig.Builder()
                .dataSource(td)
                .events(Components.noEvents())
                .build()
        client = LDClient("sdk-key-test", config)
        manager = LaunchDarklyFeatureFlagManager(client, observer)
    }

    @AfterTest
    fun tearDown() {
        manager.close()
    }

    private fun evalCtx(targetingKey: String = "user123") = EvaluationContext(targetingKey = targetingKey)

    @Test
    fun `factory rejects blank SDK key`() {
        assertFailsWith<IllegalArgumentException> {
            launchDarklyFeatureFlagManager(LaunchDarklyConfig(sdkKey = ""))
        }
    }

    @Test
    fun `toLDContext carries targeting key and attributes`() {
        val ctx = toLDContext(EvaluationContext("user123", mapOf("plan" to "pro", "region" to "us-east")))
        assertEquals("user123", ctx.key)
        assertEquals("pro", ctx.getValue("plan").stringValue())
        assertEquals("us-east", ctx.getValue("region").stringValue())
    }

    @Test
    fun `canUseFeature returns configured value and observes fields`() =
        runTest {
            assertTrue(manager.canUseFeature("bool-flag", evalCtx()))
            observer.assertObservedOperationWithValues(
                Keys.USER_ID to "user123",
                FlagAttributes.FEATURE to "bool-flag",
                FlagAttributes.FLAG_VALUE to true,
            )
        }

    @Test
    fun `canUseFeature returns false when flag not found`() =
        runTest {
            assertFalse(manager.canUseFeature("nonexistent", evalCtx()))
            assertTrue(observer.operations.any { it.errors.isNotEmpty() })
        }

    @Test
    fun `getStringValue returns configured value`() =
        runTest {
            assertEquals("hello-world", manager.getStringValue("string-flag", "fallback", evalCtx()))
        }

    @Test
    fun `getStringValue returns default when flag not found`() =
        runTest {
            assertEquals("fallback", manager.getStringValue("nonexistent", "fallback", evalCtx()))
        }

    @Test
    fun `getInt64Value returns configured value`() =
        runTest {
            assertEquals(42L, manager.getInt64Value("int-flag", 7L, evalCtx()))
        }

    @Test
    fun `getInt64Value returns default when flag not found`() =
        runTest {
            assertEquals(7L, manager.getInt64Value("nonexistent", 7L, evalCtx()))
        }

    @Test
    fun `getFloat64Value returns configured value`() =
        runTest {
            assertEquals(3.14, manager.getFloat64Value("float-flag", 1.0, evalCtx()), 1e-9)
        }

    @Test
    fun `getFloat64Value returns default when flag not found`() =
        runTest {
            assertEquals(1.0, manager.getFloat64Value("nonexistent", 1.0, evalCtx()), 1e-9)
        }

    @Test
    fun `getObjectValue returns configured value`() =
        runTest {
            assertEquals(mapOf("key" to "value"), manager.getObjectValue("object-flag", null, evalCtx()))
        }

    @Test
    fun `getObjectValue returns default when flag not found`() =
        runTest {
            val def = mapOf("k" to "v")
            assertEquals(def, manager.getObjectValue("nonexistent", def, evalCtx()))
        }
}
