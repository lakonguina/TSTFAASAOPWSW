package com.khazaddum.forcelist

import android.content.Context

/**
 * État persistant + règles d'auto-engagement façon Pluckeye :
 *   - SERRER (bloquer une app, ajouter une extension, augmenter le délai) = immédiat.
 *   - DESSERRER (débloquer, retirer une extension, baisser le délai) = ne prend effet
 *     qu'après le délai courant. En attendant, l'action est "en attente" et visible.
 *
 * Tout est stocké dans SharedPreferences ; [mature] applique les attentes échues.
 * Les opérations modifient seulement l'intention ; c'est [PolicyManager.reconcile]
 * qui pousse l'état voulu vers l'appareil.
 */
class Store private constructor(private val sp: android.content.SharedPreferences) {

    companion object {
        fun get(context: Context) =
            Store(context.applicationContext.getSharedPreferences("forcelist", Context.MODE_PRIVATE))

        private const val K_DELAY = "delayMin"
        private const val K_BLOCKED = "blocked"
        private const val K_KNOWN = "known"
        private const val K_EXT = "ext"
        private const val K_PENDING = "pending"
        // Types d'action en attente, encodés "KIND\u001Fpayload\u001FeffectiveMillis".
        private const val UB = "UB" // débloquer une app     (payload = package)
        private const val DD = "DD" // baisser le délai       (payload = minutes)
        private const val XR = "XR" // retirer une extension  (payload = "id;url")
        private const val SEP = "\u001F"
    }

    // --- lecture d'état ---
    val delayMinutes: Long get() = sp.getLong(K_DELAY, 0L)
    val blocked: Set<String> get() = sp.getStringSet(K_BLOCKED, emptySet())!!.toSet()
    val known: Set<String> get() = sp.getStringSet(K_KNOWN, emptySet())!!.toSet()
    val extensions: Set<String> get() = sp.getStringSet(K_EXT, emptySet())!!.toSet()

    private fun pending(): MutableList<Triple<String, String, Long>> =
        sp.getStringSet(K_PENDING, emptySet())!!.mapNotNull { s ->
            val p = s.split(SEP); if (p.size == 3) Triple(p[0], p[1], p[2].toLong()) else null
        }.toMutableList()

    private fun savePending(list: List<Triple<String, String, Long>>) =
        sp.edit().putStringSet(K_PENDING, list.map { "${it.first}$SEP${it.second}$SEP${it.third}" }.toSet()).apply()

    /** Instant d'effet d'une attente visant [kind]/[payload], ou null si aucune. */
    fun pendingAt(kind: String, payload: String): Long? =
        pending().firstOrNull { it.first == kind && it.second == payload }?.third

    fun pendingUnblockAt(pkg: String) = pendingAt(UB, pkg)
    fun pendingExtRemovalAt(ext: String) = pendingAt(XR, ext)

    /** Déblocages d'apps en attente : package -> instant d'effet. */
    fun pendingUnblocks(): Map<String, Long> =
        pending().filter { it.first == UB }.associate { it.second to it.third }

    /** Retraits d'extensions en attente : "id;url" -> instant d'effet. */
    fun pendingExtRemovals(): Map<String, Long> =
        pending().filter { it.first == XR }.associate { it.second to it.third }
    fun pendingDelay(): Pair<Long, Long>? =
        pending().firstOrNull { it.first == DD }?.let { it.second.toLong() to it.third }

    private fun now() = System.currentTimeMillis()
    private fun effective() = now() + delayMinutes * 60_000L

    private fun cancelPending(kind: String, payload: String) =
        savePending(pending().filterNot { it.first == kind && it.second == payload })

    // --- opérations apps ---
    fun block(pkg: String) {
        cancelPending(UB, pkg) // annuler un éventuel déblocage en attente (on resserre)
        sp.edit()
            .putStringSet(K_BLOCKED, blocked + pkg)
            .putStringSet(K_KNOWN, known + pkg)
            .apply()
    }

    fun unblock(pkg: String) {
        if (delayMinutes <= 0L) {
            sp.edit().putStringSet(K_BLOCKED, blocked - pkg).apply()
        } else if (pendingUnblockAt(pkg) == null && pkg in blocked) {
            savePending(pending() + Triple(UB, pkg, effective()))
        }
    }

    // --- opérations extensions ---
    fun addExtension(ext: String) {
        cancelPending(XR, ext)
        sp.edit().putStringSet(K_EXT, extensions + ext).apply()
    }

    fun removeExtension(ext: String) {
        if (delayMinutes <= 0L) {
            sp.edit().putStringSet(K_EXT, extensions - ext).apply()
        } else if (pendingExtRemovalAt(ext) == null && ext in extensions) {
            savePending(pending() + Triple(XR, ext, effective()))
        }
    }

    // --- délai ---
    fun requestDelay(newMin: Long) {
        savePending(pending().filterNot { it.first == DD }) // une seule baisse en attente à la fois
        if (newMin >= delayMinutes) {
            sp.edit().putLong(K_DELAY, newMin).apply() // augmenter = immédiat
        } else {
            savePending(pending() + Triple(DD, newMin.toString(), effective())) // baisser = retardé
        }
    }

    /** Applique toutes les attentes échues. Renvoie l'instant de la prochaine échéance. */
    fun mature(): Long? {
        val n = now()
        val (due, rest) = pending().partition { it.third <= n }
        if (due.isNotEmpty()) {
            var newBlocked = blocked
            var newExt = extensions
            var newDelay = delayMinutes
            for ((kind, payload, _) in due) when (kind) {
                UB -> newBlocked = newBlocked - payload
                XR -> newExt = newExt - payload
                DD -> newDelay = payload.toLong()
            }
            sp.edit()
                .putStringSet(K_BLOCKED, newBlocked)
                .putStringSet(K_EXT, newExt)
                .putLong(K_DELAY, newDelay)
                .apply()
            savePending(rest)
        }
        return rest.minOfOrNull { it.third }
    }
}
