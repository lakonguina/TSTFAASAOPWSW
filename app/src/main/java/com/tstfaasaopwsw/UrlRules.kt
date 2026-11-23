package com.tstfaasaopwsw

/**
 * Web blocking rules, shared by the DNS firewall and the in-house browser.
 *
 * A pattern is a domain (`x.com`) or a URL (`https://x.com/settings`). DNS can only filter
 * at the domain level; the browser filters at the path level: blocking `.../settings`
 * refuses `/settings` and its sub-pages, but not `/home`. A pattern with no path (bare
 * domain) blocks the whole domain on both sides.
 */
object UrlRules {

    /** Host of a pattern or URL: without scheme, path, query or port. */
    fun host(s: String): String {
        val afterScheme = s.trim().lowercase().substringAfter("://", s.trim().lowercase())
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    }

    /** Path of a pattern or URL: starts with '/', or "" if none. */
    fun path(s: String): String {
        val afterScheme = s.trim().lowercase().substringAfter("://", s.trim().lowercase())
        val afterHost = afterScheme.substringBefore('?').substringBefore('#')
        val slash = afterHost.indexOf('/')
        return if (slash < 0) "" else afterHost.substring(slash)
    }

    /** DNS: the queried domain is the pattern's host or one of its sub-domains. */
    fun matchesDomain(domain: String, pattern: String): Boolean {
        val h = host(pattern)
        return h.isNotEmpty() && (domain == h || domain.endsWith(".$h"))
    }

    /** Browser: same host (or sub-domain) AND path = the pattern's or a sub-page. */
    fun matchesUrl(url: String, pattern: String): Boolean {
        if (!matchesDomain(host(url), pattern)) return false
        val rulePath = path(pattern)
        if (rulePath.isEmpty() || rulePath == "/") return true // bare domain -> whole site
        val urlPath = path(url).ifEmpty { "/" }
        return urlPath == rulePath || urlPath.startsWith("$rulePath/")
    }
}
