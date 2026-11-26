package com.tstfaasaopwsw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lists ("playlists") over rules: default list, membership, move, and delete fallback. */
class RuleListsTest {

    private fun store() = Store.fromPrefs(FakeSharedPreferences())

    @Test fun a_default_list_always_exists() {
        assertTrue(store().ruleLists().any { it.isDefault && it.id == DEFAULT_LIST_ID })
    }

    @Test fun new_rules_land_in_the_default_list() {
        val s = store()
        s.block("com.app"); s.blockWeb("x.com")
        assertEquals(setOf("app:com.app", "web:x.com"), s.membersOf(DEFAULT_LIST_ID).toSet())
    }

    @Test fun a_rule_can_be_moved_to_another_list() {
        val s = store()
        s.block("com.app"); s.blockWeb("x.com")
        val id = s.createList("Social")
        s.assignToList("web:x.com", id)
        assertEquals(listOf("web:x.com"), s.membersOf(id))
        assertEquals(listOf("app:com.app"), s.membersOf(DEFAULT_LIST_ID))
    }

    @Test fun deleting_a_list_returns_its_rules_to_default() {
        val s = store()
        s.blockWeb("x.com")
        val id = s.createList("Social")
        s.assignToList("web:x.com", id)
        s.deleteList(id)
        assertFalse(s.ruleLists().any { it.id == id })
        assertEquals(listOf("web:x.com"), s.membersOf(DEFAULT_LIST_ID))
    }

    @Test fun the_default_list_cannot_be_deleted() {
        val s = store()
        s.deleteList(DEFAULT_LIST_ID)
        assertTrue(s.ruleLists().any { it.isDefault })
    }

    @Test fun removing_a_rule_drops_it_from_every_list() {
        val s = store()
        s.blockWeb("x.com")
        assertEquals(listOf("web:x.com"), s.membersOf(DEFAULT_LIST_ID))
        s.unblockWeb("x.com") // delay 0 -> immediate
        assertTrue(s.membersOf(DEFAULT_LIST_ID).isEmpty())
    }
}
