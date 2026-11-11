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
        private const val K_INSTALL_BLOCK = "installBlock"
        private const val K_WEB = "web"
        private const val K_FW = "firewall"
        private const val K_HISTORY = "history"
        private const val K_PENDING = "pending"
        // Types d'action en attente, encodés "KIND\u001Fpayload\u001FeffectiveMillis".
        private const val UB = "UB" // débloquer une app     (payload = package)
        private const val DD = "DD" // baisser le délai       (payload = minutes)
        private const val XR = "XR" // retirer une extension  (payload = "id;url")
        private const val WR = "WR" // retirer une règle web  (payload = motif)
        private const val SEP = "\u001F"
    }

    // --- lecture d'état ---
    val delayMinutes: Long get() = sp.getLong(K_DELAY, 0L)
    val blocked: Set<String> get() = sp.getStringSet(K_BLOCKED, emptySet())!!.toSet()
    val known: Set<String> get() = sp.getStringSet(K_KNOWN, emptySet())!!.toSet()
    val extensions: Set<String> get() = sp.getStringSet(K_EXT, emptySet())!!.toSet()

    /**
     * Packages qu'on ne veut pas voir revenir : une app bloquée l'est aussi, pour
     * qu'une désinstallation/réinstallation ne serve pas de contournement. Lu par
     * [InstallGuard] qui re-gèle le package dès qu'il réapparaît.
     */
    val installBlocked: Set<String> get() = sp.getStringSet(K_INSTALL_BLOCK, emptySet())!!.toSet()
    fun isInstallBlocked(pkg: String): Boolean = pkg in installBlocked

    /** Motifs interdits dans le navigateur : une URL qui en contient un est refusée. */
    val blockedWeb: Set<String> get() = sp.getStringSet(K_WEB, emptySet())!!.toSet()

    /** Pare-feu réseau actif ? (phase de test : bascule immédiate dans les deux sens). */
    val firewallEnabled: Boolean get() = sp.getBoolean(K_FW, false)
    fun setFirewallEnabled(on: Boolean) = sp.edit().putBoolean(K_FW, on).apply()

    // --- historique de navigation (plus récent en tête, borné) ---
    fun history(): List<String> =
        sp.getString(K_HISTORY, "")!!.split('\n').filter { it.isNotEmpty() }

    fun addHistory(url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val list = history().toMutableList()
        list.remove(url); list.add(0, url)
        while (list.size > 200) list.removeAt(list.size - 1)
        sp.edit().putString(K_HISTORY, list.joinToString("\n")).apply()
    }

    fun clearHistory() = sp.edit().remove(K_HISTORY).apply()

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
    fun pendingWebRemovalAt(pattern: String) = pendingAt(WR, pattern)

    /** Déblocages d'apps en attente : package -> instant d'effet. */
    fun pendingUnblocks(): Map<String, Long> =
        pending().filter { it.first == UB }.associate { it.second to it.third }

    /** Retraits d'extensions en attente : "id;url" -> instant d'effet. */
    fun pendingExtRemovals(): Map<String, Long> =
        pending().filter { it.first == XR }.associate { it.second to it.third }
    /** Retraits de règles web en attente : motif -> instant d'effet. */
    fun pendingWebRemovals(): Map<String, Long> =
        pending().filter { it.first == WR }.associate { it.second to it.third }
    fun pendingDelay(): Pair<Long, Long>? =
        pending().firstOrNull { it.first == DD }?.let { it.second.toLong() to it.third }

    private fun now() = System.currentTimeMillis()
    private fun effective() = now() + delayMinutes * 60_000L

    private fun cancelPending(kind: String, payload: String) =
        savePending(pending().filterNot { it.first == kind && it.second == payload })

    // --- opérations apps ---
    // Bloquer une app = la geler maintenant ET empêcher son retour par réinstallation.
    // Les deux facettes suivent la même intention et le même engagement.
    fun block(pkg: String) {
        cancelPending(UB, pkg) // annuler un éventuel déblocage en attente (on resserre)
        sp.edit()
            .putStringSet(K_BLOCKED, blocked + pkg)
            .putStringSet(K_INSTALL_BLOCK, installBlocked + pkg)
            .putStringSet(K_KNOWN, known + pkg)
            .apply()
    }

    fun unblock(pkg: String) {
        if (delayMinutes <= 0L) {
            sp.edit()
                .putStringSet(K_BLOCKED, blocked - pkg)
                .putStringSet(K_INSTALL_BLOCK, installBlocked - pkg)
                .apply()
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

    // --- opérations web ---
    fun blockWeb(pattern: String) {
        cancelPending(WR, pattern)
        sp.edit().putStringSet(K_WEB, blockedWeb + pattern).apply()
    }

    fun unblockWeb(pattern: String) {
        if (delayMinutes <= 0L) {
            sp.edit().putStringSet(K_WEB, blockedWeb - pattern).apply()
        } else if (pendingWebRemovalAt(pattern) == null && pattern in blockedWeb) {
            savePending(pending() + Triple(WR, pattern, effective()))
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
            var newInstallBlocked = installBlocked
            var newWeb = blockedWeb
            var newDelay = delayMinutes
            for ((kind, payload, _) in due) when (kind) {
                UB -> { newBlocked = newBlocked - payload; newInstallBlocked = newInstallBlocked - payload }
                XR -> newExt = newExt - payload
                WR -> newWeb = newWeb - payload
                DD -> newDelay = payload.toLong()
            }
            sp.edit()
                .putStringSet(K_BLOCKED, newBlocked)
                .putStringSet(K_EXT, newExt)
                .putStringSet(K_INSTALL_BLOCK, newInstallBlocked)
                .putStringSet(K_WEB, newWeb)
                .putLong(K_DELAY, newDelay)
                .apply()
            savePending(rest)
        }
        return rest.minOfOrNull { it.third }
    }
}
