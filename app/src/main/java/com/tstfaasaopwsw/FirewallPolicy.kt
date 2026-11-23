package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build

/**
 * Active/désactive le pare-feu réseau. Device Owner : il pose l'app en VPN
 * always-on sans dialogue de consentement.
 *
 * Phase de test : [lockdown] = false, donc une panne du filtre ne coupe PAS
 * l'accès réseau. Une fois éprouvé, passer à true (plus aucun trafic hors VPN).
 */
object FirewallPolicy {

    fun setFirewall(context: Context, enabled: Boolean, lockdown: Boolean = false) {
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
        Store.get(context).setFirewallEnabled(enabled)
    }

    /**
     * Sans ça, le « DNS privé » (DoT, chiffré) court-circuite notre filtre : les
     * requêtes partent chiffrées vers un résolveur externe au lieu de passer en clair
     * par notre VPN, et aucun domaine bloqué n'est intercepté.
     *
     * Le mode « opportuniste » force le résolveur à viser le DNS du VPN et à retomber
     * en clair (notre faux résolveur ne répond pas en DoT), ce qui réactive le filtre.
     * Il écrase aussi un mode « strict » imposé par le réseau, qui contournerait tout.
     */
    private fun forceCleartextDns(dpm: DevicePolicyManager, admin: android.content.ComponentName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { dpm.setGlobalPrivateDnsModeOpportunistic(admin) }
        }
    }
}
