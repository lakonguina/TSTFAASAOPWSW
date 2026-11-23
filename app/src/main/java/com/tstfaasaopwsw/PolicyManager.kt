package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager

data class PolicyReport(val isDeviceOwner: Boolean, val text: String)

/**
 * Matures due pending actions, then pushes the desired [Store] state to the device.
 * Idempotent and self-healing: callable at will (app open, alarm, boot).
 */
object PolicyManager {

    fun reconcile(context: Context): PolicyReport {
        val store = Store.get(context)
        val nextWake = store.mature()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.componentName(context)
        val pkg = context.packageName
        if (!dpm.isDeviceOwnerApp(pkg)) return notOwnerReport(pkg)

        val lines = mutableListOf<String>()
        if (!store.repaired) { // one-time orphan thaw after a reinstall
            OrphanRepair.run(dpm, admin, context.packageManager, store.blocked, pkg)
            store.markRepaired()
        }
        applyAppBlocks(dpm, admin, store, lines)
        hardenAndDefaultBrowser(dpm, admin, pkg, lines)
        PolicyAlarm.schedule(context, nextWake)
        if (nextWake != null) {
            val mins = ((nextWake - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
            lines += "Next due in ~$mins min"
        }
        return PolicyReport(true, "✅ Device Owner active.\n\n" + lines.joinToString("\n"))
    }

    private fun notOwnerReport(pkg: String) = PolicyReport(false, buildString {
        appendLine("⛔ Not Device Owner.")
        appendLine()
        appendLine("On a reset phone, with no Google account:")
        appendLine("adb shell dpm set-device-owner $pkg/.AdminReceiver")
    })

    // Each managed app: hidden (invisible) AND suspended (frozen), so a blocked app
    // can't be reopened. On unblock: the reverse.
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
        lines += "Blocked apps: ${blocked.size}"
    }

    private fun hardenAndDefaultBrowser(
        dpm: DevicePolicyManager, admin: ComponentName, pkg: String, lines: MutableList<String>,
    ) {
        runCatching { dpm.setUninstallBlocked(admin, pkg, true) }
        // Safe boot disabled: otherwise a safe-mode reboot would disable all control.
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
        lines += "Default browser: enforced"
    }
}
