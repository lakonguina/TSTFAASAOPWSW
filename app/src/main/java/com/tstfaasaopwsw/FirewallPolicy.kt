package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build

/**
 * Enables/disables the network firewall. As Device Owner it sets the app as an always-on
 * VPN with no consent dialog.
 *
 * Test phase: [lockdown] = false, so a filter failure does NOT cut network access. Once
 * proven, switch to true (no more traffic outside the VPN).
 */
object FirewallPolicy {

    /** Pushes the given state to the device. Idempotent; called from [PolicyManager.reconcile]. */
    fun apply(context: Context, enabled: Boolean, lockdown: Boolean = false) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.componentName(context)
        val pkg = context.packageName
        if (!dpm.isDeviceOwnerApp(pkg)) return
        runCatching {
            if (enabled) {
                dpm.setAlwaysOnVpnPackage(admin, pkg, lockdown)
                forceCleartextDns(dpm, admin)
            } else {
                dpm.setAlwaysOnVpnPackage(admin, null, false)
            }
        }
    }

    /**
     * Without this, "Private DNS" (DoT, encrypted) bypasses our filter: queries leave
     * encrypted toward an external resolver instead of going cleartext through our VPN,
     * and no blocked domain is intercepted.
     *
     * "Opportunistic" mode forces the resolver to target the VPN's DNS and fall back to
     * cleartext (our fake resolver does not answer over DoT), which re-enables the filter.
     * It also overrides a "strict" mode imposed by the network, which would bypass everything.
     */
    private fun forceCleartextDns(dpm: DevicePolicyManager, admin: android.content.ComponentName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { dpm.setGlobalPrivateDnsModeOpportunistic(admin) }
        }
    }
}
