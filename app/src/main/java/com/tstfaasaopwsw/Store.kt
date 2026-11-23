package com.tstfaasaopwsw

import android.content.Context
import android.content.SharedPreferences
import com.tstfaasaopwsw.PendingQueue.Companion.DD
import com.tstfaasaopwsw.PendingQueue.Companion.UB
import com.tstfaasaopwsw.PendingQueue.Companion.WR

/**
 * Persistent state + self-commitment rules, Pluckeye-style:
 *   - TIGHTEN (block, raise the delay) = immediate.
 *   - LOOSEN (unblock, lower the delay) = deferred by the current delay; meanwhile the
 *     action is "pending" (see [PendingQueue]) and stays locked.
 *
 * The "tighten / loosen" mutations are extension functions in [StoreOps].
 */
class Store private constructor(internal val sp: SharedPreferences) {

    internal val pending = PendingQueue(sp)
    private val historyStore = BrowsingHistory(sp)
    private val browserState = BrowserState(sp)

    private fun set(key: String) = sp.getStringSet(key, emptySet())!!.toSet()
    internal fun putSets(vararg e: Pair<String, Set<String>>) =
        sp.edit().apply { e.forEach { putStringSet(it.first, it.second) } }.apply()

    internal fun effective() = System.currentTimeMillis() + delayMinutes * 60_000L

    // --- state reads ---
    val delayMinutes: Long get() = sp.getLong(K_DELAY, 0L)
    val blocked: Set<String> get() = set(K_BLOCKED)
    val known: Set<String> get() = set(K_KNOWN)
    val installBlocked: Set<String> get() = set(K_INSTALL_BLOCK)
    fun isInstallBlocked(pkg: String): Boolean = pkg in installBlocked
    val blockedWeb: Set<String> get() = set(K_WEB)
    val firewallEnabled: Boolean get() = sp.getBoolean(K_FW, false)
    fun setFirewallEnabled(on: Boolean) = sp.edit().putBoolean(K_FW, on).apply()

    /** False until the post-reinstall orphan thaw has run. */
    val repaired: Boolean get() = sp.getBoolean(K_REPAIRED, false)
    fun markRepaired() = sp.edit().putBoolean(K_REPAIRED, true).apply()

    // --- open tabs (delegated) ---
    fun savedTabs(): List<String> = browserState.tabs()
    fun savedTabIndex(): Int = browserState.currentIndex()
    fun saveTabs(urls: List<String>, current: Int) = browserState.save(urls, current)

    // --- history (delegated) ---
    fun history(): List<String> = historyStore.entries()
    fun addHistory(url: String) = historyStore.add(url)
    fun clearHistory() = historyStore.clear()

    // --- pending actions (delegated) ---
    fun pendingUnblockAt(pkg: String) = pending.at(UB, pkg)
    fun pendingWebRemovalAt(pattern: String) = pending.at(WR, pattern)
    fun pendingUnblocks(): Map<String, Long> = pending.byKind(UB)
    fun pendingWebRemovals(): Map<String, Long> = pending.byKind(WR)
    fun pendingDelay(): Pair<Long, Long>? = pending.delay()

    /** Applies due pending actions. Returns the next due instant. */
    fun mature(): Long? {
        val due = pending.takeDue(System.currentTimeMillis())
        if (due.isNotEmpty()) applyDue(due)
        return pending.nextDue()
    }

    private fun applyDue(due: List<Triple<String, String, Long>>) {
        var b = blocked; var ib = installBlocked; var w = blockedWeb; var d = delayMinutes
        for ((kind, payload, _) in due) when (kind) {
            UB -> { b = b - payload; ib = ib - payload }
            WR -> w = w - payload
            DD -> d = payload.toLong()
        }
        sp.edit().putStringSet(K_BLOCKED, b)
            .putStringSet(K_INSTALL_BLOCK, ib).putStringSet(K_WEB, w).putLong(K_DELAY, d).apply()
    }

    companion object {
        fun get(context: Context) =
            Store(context.applicationContext.getSharedPreferences("forcelist", Context.MODE_PRIVATE))

        internal const val K_DELAY = "delayMin"
        internal const val K_BLOCKED = "blocked"
        internal const val K_KNOWN = "known"
        internal const val K_INSTALL_BLOCK = "installBlock"
        internal const val K_WEB = "web"
        private const val K_FW = "firewall"
        private const val K_REPAIRED = "repaired"
    }
}
