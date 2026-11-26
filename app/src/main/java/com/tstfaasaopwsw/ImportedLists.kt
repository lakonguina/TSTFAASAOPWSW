package com.tstfaasaopwsw

/**
 * Imported domain blocklists (e.g. a "no porn" hosts list from GitHub). Each imported list's
 * domains are stored under `imp:<listId>`, newline-joined. Because a blocklist can hold tens
 * of thousands of domains, they are NOT put in [Store.blockedWeb] (a per-entry scan would
 * crawl): instead both the DNS firewall and the in-house browser test a host against one
 * combined [HashSet] by walking its labels (host, parent, grandparent…) — O(labels) per
 * check, not O(domains). The combined set is cached in [ImportCache] and rebuilt on change.
 */
object ImportCache {
    @Volatile var domains: Set<String>? = null
    fun invalidate() { domains = null }
}

private fun impKey(listId: String) = "imp:$listId"

fun Store.importDomains(listId: String): Set<String> =
    sp.getString(impKey(listId), "")!!.split('\n').filterTo(HashSet()) { it.isNotEmpty() }

fun Store.importCount(listId: String): Int = importDomains(listId).size

fun Store.setImportDomains(listId: String, domains: Set<String>) {
    sp.edit().putString(impKey(listId), domains.joinToString("\n")).apply()
    ImportCache.invalidate()
}

fun Store.clearImportDomains(listId: String) {
    sp.edit().remove(impKey(listId)).apply()
    ImportCache.invalidate()
}

/** Every blocked domain across all imported lists, as one set (cached). */
fun Store.allImportedDomains(): Set<String> {
    ImportCache.domains?.let { return it }
    val all = HashSet<String>()
    ruleLists().filter { it.imported }.forEach { all += importDomains(it.id) }
    ImportCache.domains = all
    return all
}

/** True when [host] (or any parent domain) is in the imported blocklists. O(labels). */
fun Store.isDomainImportBlocked(host: String): Boolean = suffixMatch(host.lowercase(), allImportedDomains())

/** True when the imported blocklists cover [url]'s host. */
fun Store.isUrlImportBlocked(url: String): Boolean = isDomainImportBlocked(UrlRules.host(url))

/** Walks a host up its labels (a.b.c → a.b.c, b.c, c) testing each against [set]. */
fun suffixMatch(host: String, set: Set<String>): Boolean {
    if (set.isEmpty() || host.isEmpty()) return false
    var h = host
    while (true) {
        if (h in set) return true
        val dot = h.indexOf('.')
        if (dot < 0) return false
        h = h.substring(dot + 1)
    }
}

/**
 * Parses a hosts-file / domain-list blocklist into bare domains. Handles `0.0.0.0 domain`,
 * `127.0.0.1 domain`, `::1 domain` and bare `domain` lines; drops comments (`#`), blanks and
 * `localhost`. Accepts only entries with a dot (real domains).
 */
fun parseBlocklist(text: String): Set<String> {
    val out = HashSet<String>()
    text.lineSequence().forEach { raw ->
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return@forEach
        val parts = line.split(Regex("\\s+"))
        val domain = when {
            parts.size >= 2 && parts[0] in SINK_IPS -> parts[1]
            parts.size == 1 -> parts[0]
            else -> return@forEach
        }.lowercase().removePrefix("www.")
        if (domain.contains('.') && domain != "localhost" && !domain.startsWith("0.0.0.0")) out += domain
    }
    return out
}

private val SINK_IPS = setOf("0.0.0.0", "127.0.0.1", "::1", "::")
