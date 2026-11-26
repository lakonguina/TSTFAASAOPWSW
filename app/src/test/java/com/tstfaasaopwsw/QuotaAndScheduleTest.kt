package com.tstfaasaopwsw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily windows + per-rule minutes quota, and how they fold into the effective blocked sets. */
class QuotaAndScheduleTest {

    private fun store() = Store.fromPrefs(FakeSharedPreferences())

    @Test fun usage_accumulates_and_reports_remaining() {
        val s = store()
        s.setQuota("web:x.com", 2) // 120 s
        s.addUsageSeconds("web:x.com", 30)
        assertEquals(90, s.quotaRemainingSeconds("web:x.com"))
        assertFalse(s.quotaExhausted("web:x.com"))
        s.addUsageSeconds("web:x.com", 200)
        assertEquals(0, s.quotaRemainingSeconds("web:x.com"))
        assertTrue(s.quotaExhausted("web:x.com"))
    }

    @Test fun no_quota_means_no_remaining() {
        assertEquals(null, store().quotaRemainingSeconds("web:x.com"))
    }

    @Test fun tickUsage_counts_matching_pages_only() {
        val s = store()
        s.setQuota("web:x.com", 5)
        s.tickUsage("https://x.com/home")
        s.tickUsage("https://mobile.x.com/feed")
        s.tickUsage("https://google.com") // no quota facet -> ignored
        assertEquals(2, s.usedSecondsToday("web:x.com"))
    }

    @Test fun tickUsage_flags_a_feed_that_just_hit_its_limit() {
        val s = store()
        s.setQuota("elem:x.com|Feed", 1) // 60 s
        repeat(59) { assertTrue(s.tickUsage("https://x.com/home").isEmpty()) } // 59 s, not yet
        assertEquals(listOf("x.com|Feed"), s.tickUsage("https://x.com/home")) // the 60th second
        assertTrue(s.tickUsage("https://x.com/home").isEmpty())               // already past, no re-flag
    }

    @Test fun spent_web_quota_blocks_the_site() {
        val s = store()
        s.setQuota("web:x.com", 1)
        assertFalse("x.com" in s.effectiveBlockedWeb(nowMin = 12 * 60))
        s.addUsageSeconds("web:x.com", 60)
        assertTrue("x.com" in s.effectiveBlockedWeb(nowMin = 12 * 60))
    }

    @Test fun spent_feed_quota_hides_the_element() {
        val s = store()
        s.setQuota("elem:x.com|Feed", 1)
        s.addUsageSeconds("elem:x.com|Feed", 60)
        assertTrue("x.com|Feed" in s.effectiveEnabledElements(nowMin = 12 * 60))
    }

    @Test fun window_blocks_outside_allowed_hours() {
        val s = store()
        s.setWindow("web:x.com", Window(8 * 60, 22 * 60))
        assertTrue("x.com" in s.effectiveBlockedWeb(nowMin = 7 * 60))   // before 08:00
        assertFalse("x.com" in s.effectiveBlockedWeb(nowMin = 12 * 60)) // inside
    }

    @Test fun any_rule_makes_a_domain_controlled() {
        val s = store()
        s.setQuota("elem:x.com|Feed", 10)
        assertTrue("x.com" in s.controlledDomains())
    }
}
