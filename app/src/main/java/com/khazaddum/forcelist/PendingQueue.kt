package com.khazaddum.forcelist

import android.content.SharedPreferences

/**
 * File des actions « en attente » (desserrages différés façon Pluckeye), stockée dans
 * les mêmes SharedPreferences que [Store]. Chaque entrée : "KIND\u001Fpayload\u001Fmillis".
 */
class PendingQueue(private val sp: SharedPreferences) {

    private fun all(): MutableList<Triple<String, String, Long>> =
        sp.getStringSet(K_PENDING, emptySet())!!.mapNotNull { s ->
            val p = s.split(SEP); if (p.size == 3) Triple(p[0], p[1], p[2].toLong()) else null
        }.toMutableList()

    private fun save(list: List<Triple<String, String, Long>>) = sp.edit()
        .putStringSet(K_PENDING, list.map { "${it.first}$SEP${it.second}$SEP${it.third}" }.toSet())
        .apply()

    /** Instant d'effet d'une attente visant [kind]/[payload], ou null si aucune. */
    fun at(kind: String, payload: String): Long? =
        all().firstOrNull { it.first == kind && it.second == payload }?.third

    /** Toutes les attentes d'un type : payload -> instant d'effet. */
    fun byKind(kind: String): Map<String, Long> =
        all().filter { it.first == kind }.associate { it.second to it.third }

    fun delay(): Pair<Long, Long>? =
        all().firstOrNull { it.first == DD }?.let { it.second.toLong() to it.third }

    fun add(kind: String, payload: String, effectiveMillis: Long) =
        save(all() + Triple(kind, payload, effectiveMillis))

    fun cancel(kind: String, payload: String) =
        save(all().filterNot { it.first == kind && it.second == payload })

    fun cancelKind(kind: String) = save(all().filterNot { it.first == kind })

    /** Sépare les attentes échues (<= [now]) du reste, qu'on persiste. */
    fun takeDue(now: Long): List<Triple<String, String, Long>> {
        val (due, rest) = all().partition { it.third <= now }
        if (due.isNotEmpty()) save(rest)
        return due
    }

    /** Prochaine échéance restante, ou null. */
    fun nextDue(): Long? = all().minOfOrNull { it.third }

    companion object {
        private const val K_PENDING = "pending"
        private const val SEP = "\u001F"
        const val UB = "UB" // débloquer une app     (payload = package)
        const val DD = "DD" // baisser le délai       (payload = minutes)
        const val XR = "XR" // retirer une extension  (payload = "id;url")
        const val WR = "WR" // retirer une règle web  (payload = motif)
    }
}
