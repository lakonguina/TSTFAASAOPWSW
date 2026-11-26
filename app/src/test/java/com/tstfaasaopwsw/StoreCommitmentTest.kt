package com.tstfaasaopwsw

import com.tstfaasaopwsw.PendingQueue.Companion.UB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The self-commitment rules: tighten is immediate, loosen defers, maturation applies. */
class StoreCommitmentTest {

    private fun store() = Store.fromPrefs(FakeSharedPreferences())

    @Test fun block_then_unblock_with_no_delay_is_immediate() {
        val s = store()
        s.block("com.app")
        assertTrue("com.app" in s.blocked)
        s.unblock("com.app")
        assertFalse("com.app" in s.blocked)
        assertNull(s.pendingUnblockAt("com.app"))
    }

    @Test fun quick_block_then_unblock_within_grace_cancels_outright() {
        val s = store()
        s.requestDelay(20)                 // raise = immediate
        assertEquals(20L, s.delayMinutes)
        s.block("com.app")                 // stamps the grace window
        s.unblock("com.app")               // still inside grace -> undo, not defer
        assertFalse("com.app" in s.blocked)
        assertNull(s.pendingUnblockAt("com.app"))
    }

    @Test fun raising_delay_is_immediate_lowering_defers() {
        val s = store()
        s.requestDelay(20)
        assertEquals(20L, s.delayMinutes)
        s.requestDelay(5)                  // lower = deferred
        assertEquals(20L, s.delayMinutes)  // unchanged for now
        assertNotNull(s.pendingDelay())
        assertEquals(5L, s.pendingDelay()!!.first)
    }

    @Test fun mature_applies_a_due_unblock() {
        val s = store()
        s.block("com.app")
        assertTrue("com.app" in s.blocked)
        s.pending.add(UB, "com.app", System.currentTimeMillis() - 1_000L) // already due
        s.mature()
        assertFalse("com.app" in s.blocked)
        assertFalse("com.app" in s.installBlocked)
    }

    @Test fun web_block_and_unblock_no_delay() {
        val s = store()
        s.blockWeb("x.com")
        assertTrue("x.com" in s.blockedWeb)
        s.unblockWeb("x.com")
        assertFalse("x.com" in s.blockedWeb)
    }
}
