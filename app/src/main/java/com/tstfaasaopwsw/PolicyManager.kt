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
        foldAppUsage(context, store) // measure app foreground time into the shared list-quota counters
        applyAppBlocks(dpm, admin, store, context, lines)
        ElementHider.sync(store) // matured hide-rule disables take effect if the browser is live
        FirewallPolicy.apply(context, true, lockdown = store.firewallLockdown) // filter always on
        hardenAndDefaultBrowser(dpm, admin, pkg, lines)
        val wake = listOfNotNull(nextWake, store.nextWindowBoundaryMillis(), appQuotaWake(context, store)).minOrNull()
        PolicyAlarm.schedule(context, wake)
        if (wake != null) {
            val mins = ((wake - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
            lines += "Next check in ~$mins min"
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
    /** App packages whose daily minutes quota is set and already spent today (system-measured). */
    private fun quotaExhaustedApps(context: Context, store: Store): Set<String> =
        store.quotas().keys.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }
            .filter { pkg -> AppUsage.foregroundSecondsToday(context, pkg) >= (store.quotaFor(ScheduleKeys.app(pkg)) ?: 0) * 60L }
            .toSet()

    /** Soonest instant an app quota runs out (to wake and freeze it), or null. */
    private fun appQuotaWake(context: Context, store: Store): Long? =
        store.quotas().keys.filter { it.startsWith("app:") }.mapNotNull { key ->
            val pkg = key.removePrefix("app:")
            val limit = (store.quotaFor(key) ?: return@mapNotNull null) * 60L
            val used = AppUsage.foregroundSecondsToday(context, pkg)
            if (used >= limit) nextMidnightMillis() else System.currentTimeMillis() + (limit - used) * 1000L
        }.minOrNull()

    /**
     * Writes each list-quota app's system-measured foreground seconds today into the shared
     * usage counters (keyed `app:<pkg>`), so a list's shared budget counts app time the same
     * way the browser counts web/feed time. Set (not added): the system read is absolute.
     */
    private fun foldAppUsage(context: Context, store: Store) {
        for (pkg in store.quotaListAppPackages()) {
            store.setUsageSeconds(ScheduleKeys.app(pkg), AppUsage.foregroundSecondsToday(context, pkg).toInt())
        }
    }

    private fun applyAppBlocks(
        dpm: DevicePolicyManager, admin: ComponentName, store: Store, context: Context, lines: MutableList<String>,
    ) {
        val quotaApps = store.quotas().keys.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }.toSet()
        val blocked = store.effectiveBlockedApps() + quotaExhaustedApps(context, store)
        for (p in store.known + store.windowedAppPackages() + quotaApps) {
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
