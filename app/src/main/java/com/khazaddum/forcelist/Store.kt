package com.khazaddum.forcelist

import android.content.Context
import android.content.SharedPreferences
import com.khazaddum.forcelist.PendingQueue.Companion.DD
import com.khazaddum.forcelist.PendingQueue.Companion.UB
import com.khazaddum.forcelist.PendingQueue.Companion.WR
import com.khazaddum.forcelist.PendingQueue.Companion.XR

/**
 * État persistant + règles d'auto-engagement façon Pluckeye :
 *   - SERRER (bloquer, ajouter une extension, augmenter le délai) = immédiat.
 *   - DESSERRER (débloquer, retirer, baisser le délai) = effet différé du délai courant ;
 *     en attendant, l'action est « en attente » (voir [PendingQueue]) et reste verrouillée.
 *
 * Les mutations « serrer / desserrer » sont des extensions dans [StoreOps].
 */
class Store private constructor(internal val sp: SharedPreferences) {

    internal val pending = PendingQueue(sp)
    private val historyStore = BrowsingHistory(sp)

    private fun set(key: String) = sp.getStringSet(key, emptySet())!!.toSet()
    internal fun putSets(vararg e: Pair<String, Set<String>>) =
        sp.edit().apply { e.forEach { putStringSet(it.first, it.second) } }.apply()

    internal fun effective() = System.currentTimeMillis() + delayMinutes * 60_000L

    // --- lecture d'état ---
    val delayMinutes: Long get() = sp.getLong(K_DELAY, 0L)
    val blocked: Set<String> get() = set(K_BLOCKED)
    val known: Set<String> get() = set(K_KNOWN)
    val extensions: Set<String> get() = set(K_EXT)
    val installBlocked: Set<String> get() = set(K_INSTALL_BLOCK)
    fun isInstallBlocked(pkg: String): Boolean = pkg in installBlocked
    val blockedWeb: Set<String> get() = set(K_WEB)
    val firewallEnabled: Boolean get() = sp.getBoolean(K_FW, false)
    fun setFirewallEnabled(on: Boolean) = sp.edit().putBoolean(K_FW, on).apply()

    /** Faux tant que le dégel des orphelins post-réinstallation n'a pas tourné. */
    val repaired: Boolean get() = sp.getBoolean(K_REPAIRED, false)
    fun markRepaired() = sp.edit().putBoolean(K_REPAIRED, true).apply()

    // --- historique (délégué) ---
    fun history(): List<String> = historyStore.entries()
    fun addHistory(url: String) = historyStore.add(url)
    fun clearHistory() = historyStore.clear()

    // --- attentes en cours (déléguées) ---
    fun pendingUnblockAt(pkg: String) = pending.at(UB, pkg)
    fun pendingExtRemovalAt(ext: String) = pending.at(XR, ext)
    fun pendingWebRemovalAt(pattern: String) = pending.at(WR, pattern)
    fun pendingUnblocks(): Map<String, Long> = pending.byKind(UB)
    fun pendingExtRemovals(): Map<String, Long> = pending.byKind(XR)
    fun pendingWebRemovals(): Map<String, Long> = pending.byKind(WR)
    fun pendingDelay(): Pair<Long, Long>? = pending.delay()

    /** Applique les attentes échues. Renvoie l'instant de la prochaine échéance. */
    fun mature(): Long? {
        val due = pending.takeDue(System.currentTimeMillis())
        if (due.isNotEmpty()) applyDue(due)
        return pending.nextDue()
    }

    private fun applyDue(due: List<Triple<String, String, Long>>) {
        var b = blocked; var x = extensions; var ib = installBlocked; var w = blockedWeb; var d = delayMinutes
        for ((kind, payload, _) in due) when (kind) {
            UB -> { b = b - payload; ib = ib - payload }
            XR -> x = x - payload
            WR -> w = w - payload
            DD -> d = payload.toLong()
        }
        sp.edit().putStringSet(K_BLOCKED, b).putStringSet(K_EXT, x)
            .putStringSet(K_INSTALL_BLOCK, ib).putStringSet(K_WEB, w).putLong(K_DELAY, d).apply()
    }

    companion object {
        fun get(context: Context) =
            Store(context.applicationContext.getSharedPreferences("forcelist", Context.MODE_PRIVATE))

        internal const val K_DELAY = "delayMin"
        internal const val K_BLOCKED = "blocked"
        internal const val K_KNOWN = "known"
        internal const val K_EXT = "ext"
        internal const val K_INSTALL_BLOCK = "installBlock"
        internal const val K_WEB = "web"
        private const val K_FW = "firewall"
        private const val K_REPAIRED = "repaired"
    }
}
