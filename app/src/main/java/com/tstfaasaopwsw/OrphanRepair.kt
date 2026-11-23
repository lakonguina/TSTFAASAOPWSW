package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * Lève le gel des apps « orphelines » : suspendues/masquées par une instance précédente
 * de la même app (même package), mais plus suivies après une réinstallation (données
 * vierges). Sans ça, une app comme YouTube reste grisée sans que rien ne la débloque.
 *
 * À lancer une seule fois après une (ré)installation : tout ce qui n'est PAS bloqué est
 * dé-suspendu et démasqué. Sur un appareil Device Owner, notre admin est le seul à geler.
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
