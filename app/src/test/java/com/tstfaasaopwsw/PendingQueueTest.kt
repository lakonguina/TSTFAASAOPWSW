package com.tstfaasaopwsw

import com.tstfaasaopwsw.PendingQueue.Companion.UB
import com.tstfaasaopwsw.PendingQueue.Companion.WR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deferred-loosening queue: what matures, when, and what stays locked. */
class PendingQueueTest {

    private fun queue() = PendingQueue(FakeSharedPreferences())

    @Test fun add_and_lookup_by_kind_and_payload() {
        val q = queue()
        q.add(UB, "com.app", 1_000L)
        q.add(WR, "x.com", 2_000L)
        assertEquals(1_000L, q.at(UB, "com.app"))
        assertEquals(mapOf("x.com" to 2_000L), q.byKind(WR))
        assertNull(q.at(UB, "other"))
    }

    @Test fun takeDue_returns_only_matured_and_keeps_the_rest() {
        val q = queue()
        q.add(UB, "a", 100L)
        q.add(UB, "b", 5_000L)
        val due = q.takeDue(now = 1_000L)
        assertEquals(1, due.size)
        assertEquals("a", due.first().second)
        assertNull(q.at(UB, "a"))          // consumed
        assertEquals(5_000L, q.at(UB, "b")) // still pending
    }

    @Test fun cancel_removes_a_specific_pending_action() {
        val q = queue()
        q.add(UB, "a", 100L)
        q.cancel(UB, "a")
        assertNull(q.at(UB, "a"))
    }

    @Test fun nextDue_is_the_soonest() {
        val q = queue()
        q.add(UB, "a", 9_000L)
        q.add(WR, "b", 3_000L)
        assertEquals(3_000L, q.nextDue())
    }

    @Test fun empty_queue_has_no_next_due() {
        assertNull(queue().nextDue())
        assertTrue(queue().takeDue(now = Long.MAX_VALUE).isEmpty())
    }
}
