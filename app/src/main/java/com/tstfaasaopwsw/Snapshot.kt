package com.tstfaasaopwsw

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Immutable view of the state, rebuilt on every UI "tick". */
internal data class Snapshot(
    val statusText: String,
    val isDeviceOwner: Boolean,
    val blocked: Set<String>,
    val blockedWeb: Set<String>,
    val firewallEnabled: Boolean,
    val delayMinutes: Long,
    val pendingDelay: Pair<Long, Long>?,
    val pendingUnblock: Map<String, Long>,
    val pendingWebRemoval: Map<String, Long>,
)

internal data class AppInfo(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap,
    val isLaunchable: Boolean,
)

internal fun buildSnapshot(context: Context): Snapshot {
    val report = PolicyManager.reconcile(context)
    val store = Store.get(context)
    return Snapshot(
        statusText = report.text,
        isDeviceOwner = report.isDeviceOwner,
        blocked = store.blocked,
        blockedWeb = store.blockedWeb,
        firewallEnabled = store.firewallEnabled,
        delayMinutes = store.delayMinutes,
        pendingDelay = store.pendingDelay(),
        pendingUnblock = store.pendingUnblocks(),
        pendingWebRemoval = store.pendingWebRemovals(),
    )
}

/**
 * Apps the user recognizes: the launchable ones (icon in the drawer) plus those already
 * managed (so they can be unblocked even when hidden). Not the hundreds of system services.
 */
internal suspend fun loadApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val self = context.packageName
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
    val managed = Store.get(context).known
    ((launchable + managed) - self).mapNotNull { pkg ->
        runCatching {
            val ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            AppInfo(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai).toSquareBitmap(), pkg in launchable)
        }.getOrNull()
    }.sortedBy { it.label.lowercase() }
}

private fun Drawable.toSquareBitmap(px: Int = 96): ImageBitmap =
    toBitmap(width = px, height = px).asImageBitmap()
