package com.tstfaasaopwsw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web-blocking matcher shared by the firewall and the browser — the core of blocking. */
class UrlRulesTest {

    @Test fun host_strips_scheme_path_query_port() {
        assertEquals("x.com", UrlRules.host("https://x.com/home?a=1"))
        assertEquals("x.com", UrlRules.host("x.com"))
        assertEquals("x.com", UrlRules.host("http://x.com:8080/a"))
        assertEquals("x.com", UrlRules.host("X.COM/Home"))
    }

    @Test fun path_keeps_leading_slash_or_empty() {
        assertEquals("/settings", UrlRules.path("https://x.com/settings"))
        assertEquals("", UrlRules.path("x.com"))
        assertEquals("/a/b", UrlRules.path("https://x.com/a/b?q=1"))
    }

    @Test fun matchesDomain_covers_subdomains_not_siblings() {
        assertTrue(UrlRules.matchesDomain("x.com", "x.com"))
        assertTrue(UrlRules.matchesDomain("mobile.x.com", "x.com"))
        assertFalse(UrlRules.matchesDomain("notx.com", "x.com"))
        assertFalse(UrlRules.matchesDomain("x.com.evil.com", "x.com"))
    }

    @Test fun matchesUrl_bare_domain_blocks_whole_site() {
        assertTrue(UrlRules.matchesUrl("https://x.com/anything", "x.com"))
        assertTrue(UrlRules.matchesUrl("https://mobile.x.com/", "x.com"))
    }

    @Test fun matchesUrl_path_rule_blocks_only_that_subtree() {
        val rule = "https://x.com/settings"
        assertTrue(UrlRules.matchesUrl("https://x.com/settings", rule))
        assertTrue(UrlRules.matchesUrl("https://x.com/settings/profile", rule))
        assertFalse(UrlRules.matchesUrl("https://x.com/home", rule))
        assertFalse(UrlRules.matchesUrl("https://x.com/settingsother", rule))
    }

    @Test fun matchesUrl_rejects_other_host() {
        assertFalse(UrlRules.matchesUrl("https://y.com/settings", "https://x.com/settings"))
    }
}
