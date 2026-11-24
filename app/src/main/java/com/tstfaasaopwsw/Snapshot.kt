package com.tstfaasaopwsw

import android.content.Context

/** Immutable view of the state, rebuilt on every UI "tick". */
internal data class Snapshot(
    val statusText: String,
    val isDeviceOwner: Boolean,
    val blocked: Set<String>,
    val blockedWeb: Set<String>,
    val enabledElements: Set<String>,
    val firewallEnabled: Boolean,
    val delayMinutes: Long,
    val pendingDelay: Pair<Long, Long>?,
    val pendingUnblock: Map<String, Long>,
    val pendingWebRemoval: Map<String, Long>,
)

internal fun buildSnapshot(context: Context): Snapshot {
    val report = PolicyManager.reconcile(context)
    val store = Store.get(context)
    return Snapshot(
        statusText = report.text,
        isDeviceOwner = report.isDeviceOwner,
        blocked = store.blocked,
        blockedWeb = store.blockedWeb,
        enabledElements = store.enabledElements,
        firewallEnabled = store.firewallEnabled,
        delayMinutes = store.delayMinutes,
        pendingDelay = store.pendingDelay(),
        pendingUnblock = store.pendingUnblocks(),
        pendingWebRemoval = store.pendingWebRemovals(),
    )
}
