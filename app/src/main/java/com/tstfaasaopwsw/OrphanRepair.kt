package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * Thaws "orphan" apps: suspended/hidden by a previous instance of this same app (same
 * package), but no longer tracked after a reinstall (fresh data). Without this, an app
 * like YouTube stays greyed out with nothing to unblock it.
 *
 * Run once after a (re)install: everything NOT blocked is unsuspended and unhidden. On a
 * Device Owner device, our admin is the only one that freezes apps.
 */
object OrphanRepair {

    fun run(dpm: DevicePolicyManager, admin: ComponentName, pm: PackageManager, blocked: Set<String>, self: String) {
        val free = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .map { it.packageName }
            .filter { it != self && it !in blocked }
        runCatching { dpm.setPackagesSuspended(admin, free.toTypedArray(), false) }
        for (p in free) runCatching { dpm.setApplicationHidden(admin, p, false) }
    }
}
