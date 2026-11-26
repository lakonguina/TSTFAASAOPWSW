package com.tstfaasaopwsw

import java.util.Calendar

/**
 * Per-rule daily "allowed window": the app, site or feed is reachable only between
 * [startMin] and [endMin] (minutes of the day) and blocked the rest of the time. An
 * overnight window ([endMin] <= [startMin]) wraps past midnight. No window = no time limit.
 *
 * Windows are stored in [Store] keyed by facet: "app:<pkg>", "web:<domain>", "elem:<id>".
 * The effective-block helpers fold them into the blocked sets that [PolicyManager], the
 * firewall, the browser and [ElementHider] already enforce, so one window governs every
 * surface uniformly.
 */
data class Window(val startMin: Int, val endMin: Int) {
    fun allows(nowMin: Int): Boolean =
        if (startMin <= endMin) nowMin in startMin until endMin
        else nowMin >= startMin || nowMin < endMin
}

object ScheduleKeys {
    fun app(pkg: String) = "app:$pkg"
    fun web(domain: String) = "web:$domain"
    fun elem(id: String) = "elem:$id"
}

private const val K_WINDOWS = "windows"
private const val K_QUOTAS = "quotas"
private const val SEP = "\u001F"

/**
 * Daily allowed minutes per facet (same keys as the windows). Stored and shown now; the
 * usage measurement that enforces it (browser timing + UsageStatsManager) is the next
 * step, so a quota is recorded but not yet blocking.
 */
fun Store.quotas(): Map<String, Int> =
    sp.getStringSet(K_QUOTAS, emptySet())!!.mapNotNull { row ->
        val p = row.split(SEP); if (p.size == 2) p[1].toIntOrNull()?.let { p[0] to it } else null
    }.toMap()

fun Store.quotaFor(key: String): Int? = quotas()[key]

fun Store.setQuota(key: String, minutes: Int?) {
    val rows = quotas().toMutableMap()
    if (minutes == null || minutes <= 0) rows.remove(key) else rows[key] = minutes
    sp.edit().putStringSet(K_QUOTAS, rows.map { "${it.key}$SEP${it.value}" }.toSet()).apply()
}

fun Store.windows(): Map<String, Window> =
    sp.getStringSet(K_WINDOWS, emptySet())!!.mapNotNull { row ->
        val p = row.split(SEP)
        if (p.size == 3) p[0] to Window(p[1].toInt(), p[2].toInt()) else null
    }.toMap()

fun Store.windowFor(key: String): Window? = windows()[key]

fun Store.setWindow(key: String, window: Window?) {
    val rows = windows().toMutableMap()
    if (window == null) rows.remove(key) else rows[key] = window
    sp.edit().putStringSet(K_WINDOWS, rows.map { "${it.key}$SEP${it.value.startMin}$SEP${it.value.endMin}" }.toSet()).apply()
}

/** A facet is window-blocked right now when it has a window that does not allow this minute. */
private fun Map<String, Window>.blocksNow(key: String, nowMin: Int): Boolean =
    this[key]?.allows(nowMin) == false

fun Store.effectiveBlockedApps(nowMin: Int = nowMinuteOfDay()): Set<String> {
    val w = windows()
    val windowed = w.keys.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }
    // An app is also closed outside its list's hours (list quota governs web/feed, not apps).
    val listBlocked = activeFacets().filter { it.startsWith("app:") && listBlocksFacet(it, nowMin, quotaCounts = false) }
        .map { it.removePrefix("app:") }
    return blocked + windowed.filter { w.blocksNow(ScheduleKeys.app(it), nowMin) } + listBlocked
}

fun Store.effectiveBlockedWeb(nowMin: Int = nowMinuteOfDay()): Set<String> {
    val w = windows()
    val windowed = w.keys.filter { it.startsWith("web:") }.map { it.removePrefix("web:") }
    val quotaSpent = quotas().keys.filter { it.startsWith("web:") && quotaExhausted(it) }.map { it.removePrefix("web:") }
    // The facet's list can also close it: the list's hours (window) or its shared quota.
    val listBlocked = activeFacets().filter { it.startsWith("web:") && listBlocksFacet(it, nowMin, quotaCounts = true) }
        .map { it.removePrefix("web:") }
    return blockedWeb + windowed.filter { w.blocksNow(ScheduleKeys.web(it), nowMin) } + quotaSpent + listBlocked
}

/**
 * Which feed/navbar elements are hidden right now. A window means "the surface is visible
 * only inside these hours": for an element that has one, the window alone decides (hidden
 * outside it, shown inside) — even if the manual hide toggle is on — so setting allowed
 * hours on a feed lets it reappear during that window. Elements with no window fall back to
 * the manual toggle.
 */
fun Store.effectiveEnabledElements(nowMin: Int = nowMinuteOfDay()): Set<String> {
    val w = windows()
    val windowed = w.keys.filter { it.startsWith("elem:") }.map { it.removePrefix("elem:") }.toSet()
    val manual = enabledElements - windowed
    val quotaHidden = quotas().keys.filter { it.startsWith("elem:") && quotaExhausted(it) }.map { it.removePrefix("elem:") }
    val listHidden = activeFacets().filter { it.startsWith("elem:") && listBlocksFacet(it, nowMin, quotaCounts = true) }
        .map { it.removePrefix("elem:") }
    return manual + windowed.filter { w.blocksNow(ScheduleKeys.elem(it), nowMin) } + quotaHidden + listHidden
}

fun Store.windowedAppPackages(): Set<String> =
    windows().keys.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }.toSet()

/**
 * Domains the firewall closes to every other app, so a controlled site is reachable only
 * through our own (VPN-excluded) browser. A domain is controlled as soon as it has any web
 * rule — a whole-site/path block, an enabled hide rule (feed), or a web/feed schedule —
 * regardless of whether it happens to be allowed right now. Our browser keeps using
 * [effectiveBlockedWeb], so a feed-only domain still opens there (with the feed hidden).
 */
fun Store.controlledDomains(): Set<String> {
    val hosts = mutableSetOf<String>()
    blockedWeb.forEach { hosts += UrlRules.host(it) }
    enabledElements.forEach { hosts += it.substringBefore("|") }
    (windows().keys + quotas().keys).forEach { key ->
        when {
            key.startsWith("web:") -> hosts += UrlRules.host(key.removePrefix("web:"))
            key.startsWith("elem:") -> hosts += key.removePrefix("elem:").substringBefore("|")
        }
    }
    return hosts.filter { it.isNotEmpty() }.toSet()
}

/** Epoch millis of the next window edge (any start or end), when the effective state flips. */
fun Store.nextWindowBoundaryMillis(): Long? {
    val edges = windows().values.flatMap { listOf(it.startMin, it.endMin) }.toSortedSet()
    if (edges.isEmpty()) return null
    val now = Calendar.getInstance()
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val nextEdge = edges.firstOrNull { it > nowMin } ?: edges.first()
    val cal = Calendar.getInstance().apply {
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.HOUR_OF_DAY, nextEdge / 60); set(Calendar.MINUTE, nextEdge % 60)
    }
    if (nextEdge <= nowMin) cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
}

private fun nowMinuteOfDay(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}
