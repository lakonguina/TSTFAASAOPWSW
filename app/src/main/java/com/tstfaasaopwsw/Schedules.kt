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
private const val SEP = "\u001F"

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
    return blocked + windowed.filter { w.blocksNow(ScheduleKeys.app(it), nowMin) }
}

fun Store.effectiveBlockedWeb(nowMin: Int = nowMinuteOfDay()): Set<String> {
    val w = windows()
    val windowed = w.keys.filter { it.startsWith("web:") }.map { it.removePrefix("web:") }
    return blockedWeb + windowed.filter { w.blocksNow(ScheduleKeys.web(it), nowMin) }
}

fun Store.effectiveEnabledElements(nowMin: Int = nowMinuteOfDay()): Set<String> {
    val w = windows()
    val windowed = w.keys.filter { it.startsWith("elem:") }.map { it.removePrefix("elem:") }
    return enabledElements + windowed.filter { w.blocksNow(ScheduleKeys.elem(it), nowMin) }
}

fun Store.windowedAppPackages(): Set<String> =
    windows().keys.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }.toSet()

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
