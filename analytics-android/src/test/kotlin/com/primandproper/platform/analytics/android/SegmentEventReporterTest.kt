package com.primandproper.platform.analytics.android

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.Observer
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the Segment Android reporter's identity/property mapping and observability off-device: a
 * capturing [SegmentClient] stands in for the live SDK (no Context, no delivery), the on-device analog
 * of the server reporter's captured-enqueuer test.
 */
class SegmentEventReporterTest {
    private data class TrackCall(
        val event: String,
        val properties: JsonObject,
        val userId: String?,
        val anonymousId: String?,
    )

    private class FakeSegmentClient : SegmentClient {
        val identifies = mutableListOf<Pair<String, JsonObject>>()
        val tracks = mutableListOf<TrackCall>()

        override fun identify(
            userId: String,
            traits: JsonObject,
        ) {
            identifies.add(userId to traits)
        }

        override fun track(
            event: String,
            properties: JsonObject,
            userId: String?,
            anonymousId: String?,
        ) {
            tracks.add(TrackCall(event, properties, userId, anonymousId))
        }
    }

    private fun reporter(): Pair<SegmentEventReporter, FakeSegmentClient> {
        val fake = FakeSegmentClient()
        val obs: Observer = RecordingObserver()
        return SegmentEventReporter(obs, fake, closer = {}) to fake
    }

    @Test
    fun `addUser issues an identify with converted traits`() =
        runTest {
            val (reporter, fake) = reporter()

            reporter.addUser("user1", mapOf("plan" to "pro", "seats" to 5))

            val (id, traits) = fake.identifies.single()
            assertEquals("user1", id)
            assertEquals("pro", traits["plan"]?.jsonPrimitive?.content)
            assertEquals(5, traits["seats"]?.jsonPrimitive?.content?.toInt())
        }

    @Test
    fun `eventOccurred tracks with the user id stamped, not anonymous`() =
        runTest {
            val (reporter, fake) = reporter()

            reporter.eventOccurred("signup", "user1", mapOf("k" to "v"))

            val call = fake.tracks.single()
            assertEquals("signup", call.event)
            assertEquals("user1", call.userId)
            assertNull(call.anonymousId)
            assertEquals("v", call.properties["k"]?.jsonPrimitive?.content)
        }

    @Test
    fun `eventOccurredAnonymous tracks with the anonymous id stamped`() =
        runTest {
            val (reporter, fake) = reporter()

            reporter.eventOccurredAnonymous("view", "anon-9", emptyMap())

            val call = fake.tracks.single()
            assertEquals("view", call.event)
            assertNull(call.userId)
            assertEquals("anon-9", call.anonymousId)
        }

    @Test
    fun `spans record the operation fields`() =
        runTest {
            val fake = FakeSegmentClient()
            val obs = RecordingObserver()
            val reporter = SegmentEventReporter(obs, fake, closer = {})

            reporter.eventOccurred("signup", "user1", mapOf("k" to "v"))

            obs.assertObservedOperationWithValues(
                "event" to "signup",
                Keys.USER_ID to "user1",
                "anonymous" to false,
            )
        }

    @Test
    fun `close swallows closer failures`() =
        runTest {
            val obs = RecordingObserver()
            val reporter = SegmentEventReporter(obs, FakeSegmentClient(), closer = { error("boom") })

            reporter.close() // must not throw
        }

    @Test
    fun `property conversion widens booleans, numbers, and nested values`() {
        val json = mapOf("b" to true, "n" to 3.5, "s" to "x").toJsonObject()
        assertTrue(json["b"]!!.let { (it as JsonPrimitive).booleanOrNull == true })
        assertEquals("3.5", json["n"]?.jsonPrimitive?.content)
        assertEquals("x", json["s"]?.jsonPrimitive?.content)
    }
}
