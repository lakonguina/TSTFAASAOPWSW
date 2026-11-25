package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Reactive install control.
 *
 * Android does not allow refusing the install of a specific package *before* it happens.
 * The workaround: watch new apps and apply the policy the instant they appear. The window
 * is on the order of a millisecond — the app is frozen before it can be opened.
 *
 * Two cases, reusing [Store]'s commitment mechanics as-is:
 *   - package declared "never installable" (e.g. Instagram) → frozen, and it stays frozen;
 *   - package from somewhere other than a known store (sideload) → frozen, then thawed
 *     automatically after the reflection delay (time to reconsider).
 * An app installed from a known store passes through unhindered.
 */
class InstallGuard : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // update, not an install
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val store = Store.get(context)
        when {
            store.isInstallBlocked(pkg) -> store.block(pkg) // forbidden: frozen, no release
            isSystemApp(context, pkg) -> return             // preinstalled (e.g. YouTube Music): trusted
            !fromKnownStore(context, pkg) -> {              // sideload: frozen then deferred release
                store.block(pkg)
                store.unblock(pkg) // immediate if delay = 0, otherwise matures after the delay
            }
            else -> return // app from a known store: nothing to do
        }
        PolicyManager.reconcile(context)
    }

    private fun isSystemApp(context: Context, pkg: String): Boolean = runCatching {
        val flags = context.packageManager.getApplicationInfo(pkg, 0).flags
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }.getOrDefault(false)

    private fun fromKnownStore(context: Context, pkg: String): Boolean {
        val installer = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                pm.getInstallSourceInfo(pkg).installingPackageName
            else
                @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
        }.getOrNull()
        return installer in KNOWN_STORES
    }

    private companion object {
        /** Installers treated as trusted stores. */
        val KNOWN_STORES = setOf(
            "com.android.vending",            // Google Play
            "com.google.android.feedback",    // Play (variant)
            "com.sec.android.app.samsungapps", // Galaxy Store
            "com.amazon.venezia",             // Amazon Appstore
        )
    }
}
