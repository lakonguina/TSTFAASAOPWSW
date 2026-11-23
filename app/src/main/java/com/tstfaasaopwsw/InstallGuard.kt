package com.tstfaasaopwsw

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Contrôle des installations, en réactif.
 *
 * Android ne permet pas de refuser l'installation d'un package précis *avant* qu'elle
 * ait lieu. La parade : surveiller les nouvelles apps et appliquer la politique à
 * l'instant où elles apparaissent. La fenêtre est de l'ordre de la milliseconde —
 * l'app est gelée avant de pouvoir être ouverte.
 *
 * Deux cas, qui réutilisent tels quels la mécanique d'engagement de [Store] :
 *   - package déclaré « jamais installable » (ex. Instagram) → gelé, et il le reste ;
 *   - package venu d'ailleurs qu'un store connu (sideload) → gelé, puis dégelé
 *     automatiquement après le délai de réflexion (le temps d'y repenser).
 * Une app installée depuis un store connu passe sans entrave.
 */
class InstallGuard : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // mise à jour, pas une install
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val store = Store.get(context)
        when {
            store.isInstallBlocked(pkg) -> store.block(pkg) // interdit : gelé, sans levée
            !fromKnownStore(context, pkg) -> {              // sideload : gelé puis levée différée
                store.block(pkg)
                store.unblock(pkg) // immédiat si délai = 0, sinon mûrit après le délai
            }
            else -> return // app d'un store connu : rien à faire
        }
        PolicyManager.reconcile(context)
    }

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
        /** Installateurs considérés comme des stores de confiance. */
        val KNOWN_STORES = setOf(
            "com.android.vending",            // Google Play
            "com.google.android.feedback",    // Play (variante)
            "com.sec.android.app.samsungapps", // Galaxy Store
            "com.amazon.venezia",             // Amazon Appstore
        )
    }
}
