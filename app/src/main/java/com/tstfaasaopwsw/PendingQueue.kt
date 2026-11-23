package com.tstfaasaopwsw

import android.content.SharedPreferences

/**
 * Queue of "pending" actions (deferred loosening, Pluckeye-style), stored in the same
 * SharedPreferences as [Store]. Each entry: "KIND\u001Fpayload\u001Fmillis".
 */
class PendingQueue(private val sp: SharedPreferences) {

    private fun all(): MutableList<Triple<String, String, Long>> =
        sp.getStringSet(K_PENDING, emptySet())!!.mapNotNull { s ->
            val p = s.split(SEP); if (p.size == 3) Triple(p[0], p[1], p[2].toLong()) else null
        }.toMutableList()

    private fun save(list: List<Triple<String, String, Long>>) = sp.edit()
        .putStringSet(K_PENDING, list.map { "${it.first}$SEP${it.second}$SEP${it.third}" }.toSet())
        .apply()

    /** Effective instant of a pending action for [kind]/[payload], or null if none. */
    fun at(kind: String, payload: String): Long? =
        all().firstOrNull { it.first == kind && it.second == payload }?.third

    /** All pending actions of one kind: payload -> effective instant. */
    fun byKind(kind: String): Map<String, Long> =
        all().filter { it.first == kind }.associate { it.second to it.third }

    fun delay(): Pair<Long, Long>? =
        all().firstOrNull { it.first == DD }?.let { it.second.toLong() to it.third }

    fun add(kind: String, payload: String, effectiveMillis: Long) =
        save(all() + Triple(kind, payload, effectiveMillis))

    fun cancel(kind: String, payload: String) =
        save(all().filterNot { it.first == kind && it.second == payload })

    fun cancelKind(kind: String) = save(all().filterNot { it.first == kind })

    /** Splits due actions (<= [now]) from the rest, which we persist. */
    fun takeDue(now: Long): List<Triple<String, String, Long>> {
        val (due, rest) = all().partition { it.third <= now }
        if (due.isNotEmpty()) save(rest)
        return due
    }

    /** Next remaining due instant, or null. */
    fun nextDue(): Long? = all().minOfOrNull { it.third }

    companion object {
        private const val K_PENDING = "pending"
        private const val SEP = "\u001F"
        const val UB = "UB" // unblock an app      (payload = package)
        const val DD = "DD" // lower the delay      (payload = minutes)
        const val WR = "WR" // remove a web rule     (payload = pattern)
    }
}
