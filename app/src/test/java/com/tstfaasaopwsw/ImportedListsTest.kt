package com.tstfaasaopwsw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Blocklist parsing + the label-walk matcher + imported-list storage/enforcement. */
class ImportedListsTest {

    @Before fun resetCache() = ImportCache.invalidate()

    private fun store() = Store.fromPrefs(FakeSharedPreferences())

    @Test fun parse_handles_hosts_and_bare_lines_and_comments() {
        val text = """
            # a comment
            0.0.0.0 bad.com
            127.0.0.1 evil.net
            bare.org
            0.0.0.0 www.tracker.io   # inline comment
            localhost
            notadomain
        """.trimIndent()
        assertEquals(setOf("bad.com", "evil.net", "bare.org", "tracker.io"), parseBlocklist(text))
    }

    @Test fun suffix_match_covers_subdomains_only() {
        val set = setOf("bad.com")
        assertTrue(suffixMatch("bad.com", set))
        assertTrue(suffixMatch("cdn.sub.bad.com", set))
        assertFalse(suffixMatch("notbad.com", set))
        assertFalse(suffixMatch("bad.com.evil.net", set))
        assertFalse(suffixMatch("anything", emptySet()))
    }

    @Test fun imported_list_blocks_its_domains_and_subdomains() {
        val s = store()
        val id = s.createList("No porn", "https://example/hosts")
        s.setImportDomains(id, setOf("bad.com", "evil.net"))
        assertTrue(s.isDomainImportBlocked("bad.com"))
        assertTrue(s.isDomainImportBlocked("www.bad.com"))
        assertTrue(s.isUrlImportBlocked("https://sub.evil.net/page"))
        assertFalse(s.isDomainImportBlocked("good.com"))
    }

    @Test fun removing_an_imported_list_clears_its_domains() {
        val s = store()
        val id = s.createList("No porn", "https://example/hosts")
        s.setImportDomains(id, setOf("bad.com"))
        assertTrue(s.isDomainImportBlocked("bad.com"))
        s.deleteList(id)
        assertFalse(s.isDomainImportBlocked("bad.com"))
        assertFalse(s.ruleLists().any { it.id == id })
    }
}
