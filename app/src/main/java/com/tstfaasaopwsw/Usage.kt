package com.tstfaasaopwsw

import java.util.Calendar

/**
 * Daily usage counters that enforce the per-rule minutes quota (see [Store.quotas]).
 *
 * Web and feed (`web:` / `elem:`) time is measured by the in-house browser, which calls
 * [tickUsage] once a second while the matching page is in front. App (`app:`) time is read
 * from the system instead (see [AppUsage]). Counters live in the same SharedPreferences,
 * keyed by facet, and reset automatically at the first read or write of a new day.
 */

private const val K_USAGE = "usage"
private const val K_USAGE_DAY = "usageDay"
private const val USEP = "\u001F"

private fun todayStamp(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

/** Reads today's counters, wiping them first if the stored day is not today. */
private fun Store.usageRaw(): MutableMap<String, Int> {
    if (sp.getInt(K_USAGE_DAY, 0) != todayStamp()) {
        sp.edit().remove(K_USAGE).putInt(K_USAGE_DAY, todayStamp()).apply()
        return mutableMapOf()
    }
    return sp.getStringSet(K_USAGE, emptySet())!!.mapNotNull { row ->
        val p = row.split(USEP); if (p.size == 2) p[1].toIntOrNull()?.let { p[0] to it } else null
    }.toMap().toMutableMap()
}

private fun Store.saveUsage(m: Map<String, Int>) =
    sp.edit().putInt(K_USAGE_DAY, todayStamp())
        .putStringSet(K_USAGE, m.map { "${it.key}$USEP${it.value}" }.toSet()).apply()

/** Seconds spent today on the browser-measured facet [key] (web/elem). */
fun Store.usedSecondsToday(key: String): Int = usageRaw()[key] ?: 0

/** Adds [secs] to today's counter for [key] (resetting first if the day rolled over). */
fun Store.addUsageSeconds(key: String, secs: Int) {
    val m = usageRaw(); m[key] = (m[key] ?: 0) + secs; saveUsage(m)
}

/** A browser-measured (web/elem) quota that is set and already spent. */
fun Store.quotaExhausted(key: String): Boolean {
    val q = quotaFor(key) ?: return false
    return usedSecondsToday(key) >= q * 60
}

/** Seconds left on a browser-measured quota today (floored at 0), or null if no quota. */
fun Store.quotaRemainingSeconds(key: String): Int? {
    val q = quotaFor(key) ?: return null
    return (q * 60 - usedSecondsToday(key)).coerceAtLeast(0)
}

/**
 * Counts one browser tick ([seconds]) against every web/elem quota facet that [url] is on.
 * Returns the elem ids (e.g. "x.com|Feed") whose quota is reached by *this* tick, so the
 * caller can hide them now (a reload) rather than only on the next page load.
 */
fun Store.tickUsage(url: String, seconds: Int = 1): List<String> {
    val host = UrlRules.host(url)
    val q = quotas()
    if (q.isEmpty()) return emptyList()
    val m = usageRaw()
    val newlyHiddenElems = mutableListOf<String>()
    var changed = false
    for ((key, limitMin) in q) {
        val match = when {
            key.startsWith("web:") -> UrlRules.matchesUrl(url, key.removePrefix("web:"))
            key.startsWith("elem:") -> key.removePrefix("elem:").substringBefore("|") == host
            else -> false
        }
        if (!match) continue
        val before = m[key] ?: 0
        val after = before + seconds
        m[key] = after
        changed = true
        if (key.startsWith("elem:") && before < limitMin * 60 && after >= limitMin * 60) {
            newlyHiddenElems += key.removePrefix("elem:")
        }
    }
    if (changed) saveUsage(m)
    return newlyHiddenElems
}

/** Epoch millis of the next local midnight, when all daily counters reset. */
fun nextMidnightMillis(): Long {
    val c = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}
