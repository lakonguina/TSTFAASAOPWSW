package com.tstfaasaopwsw

import com.tstfaasaopwsw.PendingQueue.Companion.DD
import com.tstfaasaopwsw.PendingQueue.Companion.ED
import com.tstfaasaopwsw.PendingQueue.Companion.UB
import com.tstfaasaopwsw.PendingQueue.Companion.WR
import com.tstfaasaopwsw.Store.Companion.K_BLOCKED
import com.tstfaasaopwsw.Store.Companion.K_DELAY
import com.tstfaasaopwsw.Store.Companion.K_ELEM
import com.tstfaasaopwsw.Store.Companion.K_INSTALL_BLOCK
import com.tstfaasaopwsw.Store.Companion.K_KNOWN
import com.tstfaasaopwsw.Store.Companion.K_WEB

/**
 * "Tighten / loosen" mutations of [Store]. Tightening is immediate; loosening with a
 * delay > 0 goes through the [PendingQueue] instead of acting right away.
 */

/**
 * Undo grace. A just-made block that is toggled straight back off (e.g. an accidental
 * double-tap) is cancelled outright instead of scheduling a deferred unblock — so a quick
 * on-off is a no-op, not a pending "Unblock in …". After [GRACE_MS] the commitment holds and
 * loosening defers as usual. The tighten instant is stamped per facet key.
 */
private const val K_TIGHTEN_AT = "tightenAt"
private const val GRACE_SEP = "\u001F"
private const val GRACE_MS = 8_000L

private fun Store.tightenMap(): MutableMap<String, Long> =
    sp.getStringSet(K_TIGHTEN_AT, emptySet())!!.mapNotNull { row ->
        val p = row.split(GRACE_SEP); if (p.size == 2) p[1].toLongOrNull()?.let { p[0] to it } else null
    }.toMap().toMutableMap()

private fun Store.saveTighten(m: Map<String, Long>) =
    sp.edit().putStringSet(K_TIGHTEN_AT, m.map { "${it.key}$GRACE_SEP${it.value}" }.toSet()).apply()

private fun Store.recordTighten(key: String) = saveTighten(tightenMap().also { it[key] = System.currentTimeMillis() })

private fun Store.inGrace(key: String): Boolean {
    val t = tightenMap()[key] ?: return false
    return System.currentTimeMillis() - t < GRACE_MS
}

private fun Store.clearTighten(key: String) {
    val m = tightenMap(); if (m.remove(key) != null) saveTighten(m)
}

// --- apps (block = freeze + prevent return via reinstall) ---
fun Store.block(pkg: String) {
    pending.cancel(UB, pkg)
    putSets(K_BLOCKED to blocked + pkg, K_INSTALL_BLOCK to installBlocked + pkg, K_KNOWN to known + pkg)
    recordTighten(ScheduleKeys.app(pkg))
}

fun Store.unblock(pkg: String) {
    val key = ScheduleKeys.app(pkg)
    if (delayMinutes <= 0L || inGrace(key)) {
        putSets(K_BLOCKED to blocked - pkg, K_INSTALL_BLOCK to installBlocked - pkg); clearTighten(key)
    } else if (pendingUnblockAt(pkg) == null && pkg in blocked) pending.add(UB, pkg, effective())
}

// --- web ---
fun Store.blockWeb(pattern: String) {
    pending.cancel(WR, pattern)
    putSets(K_WEB to blockedWeb + pattern)
    recordTighten(ScheduleKeys.web(pattern))
}

fun Store.unblockWeb(pattern: String) {
    val key = ScheduleKeys.web(pattern)
    if (delayMinutes <= 0L || inGrace(key)) {
        putSets(K_WEB to blockedWeb - pattern); clearTighten(key)
    } else if (pendingWebRemovalAt(pattern) == null && pattern in blockedWeb) pending.add(WR, pattern, effective())
}

// --- hideable elements (enabling = tighten/immediate; disabling = loosen/deferred) ---
fun Store.enableElement(id: String) {
    pending.cancel(ED, id)
    putSets(K_ELEM to enabledElements + id)
    recordTighten(ScheduleKeys.elem(id))
}

fun Store.disableElement(id: String) {
    val key = ScheduleKeys.elem(id)
    if (delayMinutes <= 0L || inGrace(key)) {
        putSets(K_ELEM to enabledElements - id); clearTighten(key)
    } else if (pendingElementAt(id) == null && id in enabledElements) pending.add(ED, id, effective())
}

// --- delay (raising = immediate; lowering = deferred) ---
fun Store.requestDelay(newMin: Long) {
    pending.cancelKind(DD)
    if (newMin >= delayMinutes) sp.edit().putLong(K_DELAY, newMin).apply()
    else pending.add(DD, newMin.toString(), effective())
}
