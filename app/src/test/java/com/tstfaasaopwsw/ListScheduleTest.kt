package com.tstfaasaopwsw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** List-level schedule: shared quota across members + list hours, folded into effective blocks. */
class ListScheduleTest {

    private fun store() = Store.fromPrefs(FakeSharedPreferences())

    @Test fun list_quota_is_shared_across_web_members() {
        val s = store()
        s.blockWeb("a.com"); s.blockWeb("b.com") // both in the default list
        s.setQuota(listKey(DEFAULT_LIST_ID), 2)  // 120 s shared
        s.addUsageSeconds("web:a.com", 50)
        s.addUsageSeconds("web:b.com", 40)
        assertEquals(90, s.listUsedSeconds(DEFAULT_LIST_ID))
        assertEquals(30, s.listQuotaRemainingSeconds(DEFAULT_LIST_ID))
        assertFalse(s.listBlocksFacet("web:a.com", nowMin = 12 * 60, quotaCounts = true))
    }

    @Test fun spending_the_shared_quota_blocks_every_member() {
        val s = store()
        s.blockWeb("a.com"); s.blockWeb("b.com")
        s.setQuota(listKey(DEFAULT_LIST_ID), 1) // 60 s shared
        s.addUsageSeconds("web:a.com", 60)      // whole budget spent by one member
        val blocked = s.effectiveBlockedWeb(nowMin = 12 * 60)
        assertTrue("a.com" in blocked)
        assertTrue("b.com" in blocked)
    }

    @Test fun list_hours_close_members_outside_the_window() {
        val s = store()
        s.blockWeb("a.com")
        s.setWindow(listKey(DEFAULT_LIST_ID), Window(8 * 60, 22 * 60))
        assertTrue(s.listBlocksFacet("web:a.com", nowMin = 7 * 60, quotaCounts = false))  // before 08:00
        assertFalse(s.listBlocksFacet("web:a.com", nowMin = 12 * 60, quotaCounts = false)) // midday
    }

    @Test fun shared_list_quota_counts_app_time_and_closes_apps_when_spent() {
        val s = store()
        s.setWindow("app:com.app", Window(0, 1440)) // a member whose own hours allow all day
        val listId = s.createList("Social")
        s.assignToList("app:com.app", listId)
        s.blockWeb("a.com"); s.assignToList("web:a.com", listId)
        s.setQuota(listKey(listId), 2) // 120 s shared
        s.setUsageSeconds("app:com.app", 90) // app foreground time, folded in like browser time
        s.addUsageSeconds("web:a.com", 10)
        assertEquals(100, s.listUsedSeconds(listId)) // app + web summed
        assertFalse("com.app" in s.effectiveBlockedApps(nowMin = 12 * 60)) // under budget
        s.setUsageSeconds("app:com.app", 120) // 130 >= 120 total -> spent
        assertTrue("com.app" in s.effectiveBlockedApps(nowMin = 12 * 60))
    }

    @Test fun list_hours_also_close_app_members() {
        val s = store()
        s.setWindow("app:com.app", Window(0, 1440)) // a rule (member) whose own hours allow all day
        val listId = s.createList("Social")
        s.assignToList("app:com.app", listId)
        s.setWindow(listKey(listId), Window(8 * 60, 22 * 60))
        assertFalse("com.app" in s.blocked) // never manually blocked
        assertTrue("com.app" in s.effectiveBlockedApps(nowMin = 23 * 60))  // past 22:00 -> list closes it
        assertFalse("com.app" in s.effectiveBlockedApps(nowMin = 12 * 60)) // midday -> open
    }
}
