package com.primandproper.platform.eventstream

import com.primandproper.platform.observability.Keys
import com.primandproper.platform.observability.testing.RecordingObserver
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of platform-go's `eventstream/manager_test.go`. */
class StreamManagerTest {
    private fun recordingManager(): Pair<StreamManager<EventStream>, RecordingObserver> {
        val obs = RecordingObserver()
        return StreamManager<EventStream>(obs) to obs
    }

    @Test
    fun `empty manager reads observe the queried group`() =
        runTest {
            val (m, obs) = recordingManager()

            assertFalse(m.groupHasStreams("any"))
            assertEquals(0, m.getStreamCount("any"))
            assertNull(m.get("any", "any"))
            assertTrue(m.getGroupStreams("any").isEmpty())

            obs.assertObservedOperationWithValues("group_id" to "any", "member_id" to "any")
            obs.assertObservedOperationWithValues("group_id" to "any", Keys.LENGTH to 0)
        }

    @Test
    fun `Add Get Remove round trip`() =
        runTest {
            val (m, obs) = recordingManager()
            val stream = MockStream()

            m.add("g1", "m1", stream)
            assertTrue(m.groupHasStreams("g1"))
            assertEquals(1, m.getStreamCount("g1"))
            assertSame(stream, m.get("g1", "m1"))
            assertEquals(1, m.getGroupStreams("g1").size)

            m.remove("g1", "m1")
            assertFalse(m.groupHasStreams("g1"))
            assertEquals(0, m.getStreamCount("g1"))
            assertNull(m.get("g1", "m1"))
            assertTrue(m.getGroupStreams("g1").isEmpty())

            obs.assertObservedOperationWithValues("group_id" to "g1", "member_id" to "m1")
        }

    @Test
    fun `Remove empties the group`() =
        runTest {
            val (m, _) = recordingManager()
            m.add("g1", "m1", MockStream())
            m.add("g1", "m2", MockStream())
            assertEquals(2, m.getStreamCount("g1"))

            m.remove("g1", "m1")
            assertEquals(1, m.getStreamCount("g1"))
            assertNotNull(m.get("g1", "m2"))

            m.remove("g1", "m2")
            assertFalse(m.groupHasStreams("g1"))
            assertEquals(0, m.getStreamCount("g1"))
        }

    @Test
    fun `Get on a missing member returns null`() =
        runTest {
            val (m, _) = recordingManager()
            assertNull(m.get("g1", "m1"))
            assertNull(m.get("", ""))
        }

    @Test
    fun `BroadcastToGroup fans out to every member`() =
        runTest {
            val (m, obs) = recordingManager()
            val s1 = MockStream()
            val s2 = MockStream()
            m.add("g1", "m1", s1)
            m.add("g1", "m2", s2)

            m.broadcastToGroup("g1", Event("test", """{"v":"hello"}"""))

            assertEquals(1, s1.events.size)
            assertEquals("test", s1.events[0].type)
            assertEquals(1, s2.events.size)

            obs.assertObservedOperationWithValues(
                "group_id" to "g1",
                "event.type" to "test",
                Keys.LENGTH to 2,
            )
        }

    @Test
    fun `BroadcastToGroup on an empty group records no length`() =
        runTest {
            val (m, obs) = recordingManager()

            m.broadcastToGroup("nonexistent", Event("test"))

            val op = obs.operations.last { it.name == "BroadcastToGroup" }
            assertEquals("nonexistent", op.values["group_id"])
            assertEquals("test", op.values["event.type"])
            assertFalse(op.values.containsKey(Keys.LENGTH))
        }

    @Test
    fun `BroadcastToGroup continues past a failing stream and records the error`() =
        runTest {
            val (m, obs) = recordingManager()
            m.add("g1", "m1", FailingStream())
            val s2 = MockStream()
            m.add("g1", "m2", s2)

            m.broadcastToGroup("g1", Event("test"))

            assertEquals(1, s2.events.size)

            val op = obs.operations.last { it.name == "BroadcastToGroup" }
            assertEquals(1, op.errors.size)
            assertEquals(2, op.values[Keys.LENGTH])
        }

    @Test
    fun `BroadcastToGroupFiltered only reaches included members`() =
        runTest {
            val (m, _) = recordingManager()
            val s1 = MockStream()
            val s2 = MockStream()
            m.add("g1", "m1", s1)
            m.add("g1", "m2", s2)

            m.broadcastToGroupFiltered("g1", Event("filtered", "\"only-m2\"")) { it == "m2" }

            assertTrue(s1.events.isEmpty())
            assertEquals(1, s2.events.size)
            assertEquals("filtered", s2.events[0].type)
        }

    @Test
    fun `BroadcastToGroupFiltered continues past a failing included stream`() =
        runTest {
            val (m, obs) = recordingManager()
            m.add("g1", "m1", FailingStream())
            val s2 = MockStream()
            m.add("g1", "m2", s2)

            m.broadcastToGroupFiltered("g1", Event("filtered")) { true }

            assertEquals(1, s2.events.size)
            val op = obs.operations.last { it.name == "BroadcastToGroupFiltered" }
            assertEquals(1, op.errors.size)
        }

    @Test
    fun `SendToMember reaches only the targeted member`() =
        runTest {
            val (m, obs) = recordingManager()
            val s1 = MockStream()
            val s2 = MockStream()
            m.add("g1", "m1", s1)
            m.add("g1", "m2", s2)

            m.sendToMember("g1", "m1", Event("direct", "\"hi\""))

            assertEquals(1, s1.events.size)
            assertEquals("direct", s1.events[0].type)
            assertTrue(s2.events.isEmpty())

            obs.assertObservedOperationWithValues(
                "group_id" to "g1",
                "member_id" to "m1",
                "event.type" to "direct",
            )
        }

    @Test
    fun `SendToMember to a missing member is a no-op`() =
        runTest {
            val (m, _) = recordingManager()
            m.sendToMember("g1", "m1", Event("x"))
            m.sendToMember("g999", "m1", Event("x"))
        }

    @Test
    fun `GetGroupStreams observes the group and its length`() =
        runTest {
            val (m, obs) = recordingManager()
            m.add("g1", "m1", MockStream())
            m.add("g1", "m2", MockStream())

            assertEquals(2, m.getGroupStreams("g1").size)
            obs.assertObservedOperationWithValues("group_id" to "g1", Keys.LENGTH to 2)

            assertTrue(m.getGroupStreams("missing").isEmpty())
            obs.assertObservedOperationWithValues("group_id" to "missing", Keys.LENGTH to 0)
        }

    @Test
    fun `Remove on a nonexistent group does not throw`() =
        runTest {
            val (m, obs) = recordingManager()
            m.remove("g1", "m1")
            obs.assertObservedOperationWithValues("group_id" to "g1", "member_id" to "m1")
        }

    @Test
    fun `broadcast does not wedge Add and Remove while a slow client blocks in send`() =
        runTest {
            val (m, _) = recordingManager()
            val slow = BlockingStream()
            m.add("g1", "m1", slow)

            val broadcast = async { m.broadcastToGroup("g1", Event("x")) }

            // Wait until the broadcast is parked inside the slow client's send.
            withTimeout(2_000) { slow.started.await() }

            // The manager lock must already be released, so a concurrent add/remove must not block.
            withTimeout(2_000) {
                val job =
                    launch {
                        m.add("g1", "m2", MockStream())
                        m.remove("g1", "m2")
                    }
                job.join()
            }

            slow.release.complete(Unit)
            broadcast.await()
        }
}
