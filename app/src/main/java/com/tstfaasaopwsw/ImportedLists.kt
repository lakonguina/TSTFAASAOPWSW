package com.tstfaasaopwsw

import android.content.Context

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

// --- periodic refresh: re-fetch each imported list's source on a schedule ---

private fun refreshKey(listId: String) = "impts:$listId"
private const val REFRESH_INTERVAL_MS = 24 * 3_600_000L // at most one re-fetch / 24 h per list

private fun Store.lastRefresh(listId: String): Long = sp.getLong(refreshKey(listId), 0L)
private fun Store.markRefreshed(listId: String) =
    sp.edit().putLong(refreshKey(listId), System.currentTimeMillis()).apply()

/**
 * Re-fetches every imported blocklist whose source hasn't been pulled in [REFRESH_INTERVAL_MS],
 * off the main thread. A refresh replaces the stored domains with the source's current set: a
 * grown upstream list tightens immediately; a shrunk one is the source's own call, not a user
 * loosening, so it applies directly. Silent on network error (keeps the last good set).
 */
object ImportedLists {
    fun refreshInBackground(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { refresh(app) } }.apply { isDaemon = true }.start()
    }

    private fun refresh(context: Context) {
        val store = Store.get(context)
        val now = System.currentTimeMillis()
        var changed = false
        for (list in store.ruleLists().filter { it.imported }) {
            val url = list.sourceUrl ?: continue
            if (now - store.lastRefresh(list.id) < REFRESH_INTERVAL_MS) continue
            store.markRefreshed(list.id) // stamp first, so a failing URL isn't retried every tick
            val domains = Downloads.getText(url)?.let { parseBlocklist(it) } ?: continue
            if (domains.isEmpty()) continue
            if (domains != store.importDomains(list.id)) { store.setImportDomains(list.id, domains); changed = true }
        }
        if (changed) PolicyManager.reconcile(context)
    }
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
