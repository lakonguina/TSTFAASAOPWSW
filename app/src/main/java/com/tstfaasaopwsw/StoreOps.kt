package com.tstfaasaopwsw

import com.tstfaasaopwsw.PendingQueue.Companion.DD
import com.tstfaasaopwsw.PendingQueue.Companion.UB
import com.tstfaasaopwsw.PendingQueue.Companion.WR
import com.tstfaasaopwsw.Store.Companion.K_BLOCKED
import com.tstfaasaopwsw.Store.Companion.K_DELAY
import com.tstfaasaopwsw.Store.Companion.K_INSTALL_BLOCK
import com.tstfaasaopwsw.Store.Companion.K_KNOWN
import com.tstfaasaopwsw.Store.Companion.K_WEB

/**
 * "Tighten / loosen" mutations of [Store]. Tightening is immediate; loosening with a
 * delay > 0 goes through the [PendingQueue] instead of acting right away.
 */

// --- apps (block = freeze + prevent return via reinstall) ---
fun Store.block(pkg: String) {
    pending.cancel(UB, pkg)
    putSets(K_BLOCKED to blocked + pkg, K_INSTALL_BLOCK to installBlocked + pkg, K_KNOWN to known + pkg)
}

fun Store.unblock(pkg: String) {
    if (delayMinutes <= 0L) putSets(K_BLOCKED to blocked - pkg, K_INSTALL_BLOCK to installBlocked - pkg)
    else if (pendingUnblockAt(pkg) == null && pkg in blocked) pending.add(UB, pkg, effective())
}

// --- web ---
fun Store.blockWeb(pattern: String) {
    pending.cancel(WR, pattern)
    putSets(K_WEB to blockedWeb + pattern)
}

fun Store.unblockWeb(pattern: String) {
    if (delayMinutes <= 0L) putSets(K_WEB to blockedWeb - pattern)
    else if (pendingWebRemovalAt(pattern) == null && pattern in blockedWeb) pending.add(WR, pattern, effective())
}

// --- delay (raising = immediate; lowering = deferred) ---
fun Store.requestDelay(newMin: Long) {
    pending.cancelKind(DD)
    if (newMin >= delayMinutes) sp.edit().putLong(K_DELAY, newMin).apply()
    else pending.add(DD, newMin.toString(), effective())
}
