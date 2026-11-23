package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.UserManager

data class PolicyReport(val isDeviceOwner: Boolean, val text: String)

/**
 * Mûrit les attentes échues, puis pousse l'état voulu de [Store] vers l'appareil.
 * Idempotent et réparateur : rappelable à volonté (ouverture d'app, alarme, boot).
 */
object PolicyManager {

    private const val FORCELIST_KEY = "ExtensionInstallForcelist"

    fun reconcile(context: Context): PolicyReport {
        val store = Store.get(context)
        val nextWake = store.mature()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.componentName(context)
        val pkg = context.packageName
        if (!dpm.isDeviceOwnerApp(pkg)) return notOwnerReport(pkg)

        val lines = mutableListOf<String>()
        if (!store.repaired) { // dégel unique des orphelins après une réinstallation
            OrphanRepair.run(dpm, admin, context.packageManager, store.blocked, pkg)
            store.markRepaired()
        }
        applyAppBlocks(dpm, admin, store, lines)
        applyExtensions(dpm, admin, store, lines)
        hardenAndDefaultBrowser(dpm, admin, pkg, lines)
        PolicyAlarm.schedule(context, nextWake)
        if (nextWake != null) {
            val mins = ((nextWake - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
            lines += "Prochaine échéance dans ~$mins min"
        }
        return PolicyReport(true, "✅ Device Owner actif.\n\n" + lines.joinToString("\n"))
    }

    private fun notOwnerReport(pkg: String) = PolicyReport(false, buildString {
        appendLine("⛔ Pas Device Owner.")
        appendLine()
        appendLine("Sur un téléphone réinitialisé, sans compte Google :")
        appendLine("adb shell dpm set-device-owner $pkg/.AdminReceiver")
    })

    // Chaque app gérée : masquée (invisible) ET suspendue (gelée), pour qu'une app
    // bloquée ne soit pas rouvrable. Au déblocage : l'inverse.
    private fun applyAppBlocks(
        dpm: DevicePolicyManager, admin: ComponentName, store: Store, lines: MutableList<String>,
    ) {
        val blocked = store.blocked
        for (p in store.known) {
            if (p in blocked) {
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(p), true) }
                runCatching { dpm.setApplicationHidden(admin, p, true) }
            } else {
                runCatching { dpm.setApplicationHidden(admin, p, false) }
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(p), false) }
            }
        }
        lines += "Apps bloquées : ${blocked.size}"
    }

    private fun applyExtensions(
        dpm: DevicePolicyManager, admin: ComponentName, store: Store, lines: MutableList<String>,
    ) {
        runCatching {
            dpm.setApplicationRestrictions(
                admin, Config.targetBrowser,
                Bundle().apply { putStringArray(FORCELIST_KEY, store.extensions.toTypedArray()) },
            )
        }
        lines += "Extensions forcées : ${store.extensions.size}"
    }

    private fun hardenAndDefaultBrowser(
        dpm: DevicePolicyManager, admin: ComponentName, pkg: String, lines: MutableList<String>,
    ) {
        runCatching { dpm.setUninstallBlocked(admin, pkg, true) }
        // Safe boot interdit : sinon un reboot en safe mode désactiverait tout contrôle.
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching {
            dpm.clearPackagePersistentPreferredActivities(admin, pkg)
            val web = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
            }
            dpm.addPersistentPreferredActivity(admin, web, ComponentName(pkg, "$pkg.BrowserActivity"))
        }
        lines += "Navigateur par défaut : imposé"
    }
}
