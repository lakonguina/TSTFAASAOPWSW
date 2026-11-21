package com.khazaddum.forcelist

import com.khazaddum.forcelist.PendingQueue.Companion.DD
import com.khazaddum.forcelist.PendingQueue.Companion.UB
import com.khazaddum.forcelist.PendingQueue.Companion.WR
import com.khazaddum.forcelist.PendingQueue.Companion.XR
import com.khazaddum.forcelist.Store.Companion.K_BLOCKED
import com.khazaddum.forcelist.Store.Companion.K_DELAY
import com.khazaddum.forcelist.Store.Companion.K_EXT
import com.khazaddum.forcelist.Store.Companion.K_INSTALL_BLOCK
import com.khazaddum.forcelist.Store.Companion.K_KNOWN
import com.khazaddum.forcelist.Store.Companion.K_WEB

/**
 * Mutations « serrer / desserrer » de [Store]. Serrer est immédiat ; desserrer avec un
 * délai > 0 passe par la file d'attente [PendingQueue] au lieu d'agir tout de suite.
 */

// --- apps (bloquer = geler + empêcher le retour par réinstallation) ---
fun Store.block(pkg: String) {
    pending.cancel(UB, pkg)
    putSets(K_BLOCKED to blocked + pkg, K_INSTALL_BLOCK to installBlocked + pkg, K_KNOWN to known + pkg)
}

fun Store.unblock(pkg: String) {
    if (delayMinutes <= 0L) putSets(K_BLOCKED to blocked - pkg, K_INSTALL_BLOCK to installBlocked - pkg)
    else if (pendingUnblockAt(pkg) == null && pkg in blocked) pending.add(UB, pkg, effective())
}

// --- extensions ---
fun Store.addExtension(ext: String) {
    pending.cancel(XR, ext)
    putSets(K_EXT to extensions + ext)
}

fun Store.removeExtension(ext: String) {
    if (delayMinutes <= 0L) putSets(K_EXT to extensions - ext)
    else if (pendingExtRemovalAt(ext) == null && ext in extensions) pending.add(XR, ext, effective())
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

// --- délai (augmenter = immédiat ; baisser = différé) ---
fun Store.requestDelay(newMin: Long) {
    pending.cancelKind(DD)
    if (newMin >= delayMinutes) sp.edit().putLong(K_DELAY, newMin).apply()
    else pending.add(DD, newMin.toString(), effective())
}
