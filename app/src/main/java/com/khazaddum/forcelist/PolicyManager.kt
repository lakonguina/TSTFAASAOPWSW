package com.khazaddum.forcelist

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserManager

data class PolicyReport(val isDeviceOwner: Boolean, val text: String)

object PolicyManager {

    private const val FORCELIST_KEY = "ExtensionInstallForcelist"
    const val ACTION_TICK = "com.khazaddum.forcelist.TICK"
    private const val ALARM_ID = 1001

    /**
     * Mûrit les attentes échues, puis pousse l'état voulu de [Store] vers l'appareil.
     * Idempotent et réparateur : rappelable à volonté (ouverture d'app, alarme, boot).
     */
    fun reconcile(context: Context): PolicyReport {
        val store = Store.get(context)
        val nextWake = store.mature()

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.componentName(context)
        val pkg = context.packageName

        if (!dpm.isDeviceOwnerApp(pkg)) {
            return PolicyReport(false, buildString {
                appendLine("⛔ Pas Device Owner.")
                appendLine()
                appendLine("Sur un téléphone réinitialisé, sans compte Google :")
                appendLine("adb shell dpm set-device-owner $pkg/.AdminReceiver")
            })
        }

        val blocked = store.blocked
        val lines = mutableListOf<String>()

        // Pour chaque app gérée : masquée (invisible) ET suspendue (gelée, non lançable
        // même si elle tournait déjà ou est atteinte par un lien). Les deux, pour qu'une
        // app bloquée ne puisse pas être rouverte. Ordre : on suspend tant qu'elle est
        // visible, puis on masque ; au déblocage, on démasque puis on lève la suspension.
        for (p in store.known) {
            val block = p in blocked
            if (block) {
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(p), true) }
                runCatching { dpm.setApplicationHidden(admin, p, true) }
            } else {
                runCatching { dpm.setApplicationHidden(admin, p, false) }
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(p), false) }
            }
        }
        lines += "Apps bloquées : ${blocked.size}"

        // Extensions forcées dans le navigateur.
        runCatching {
            dpm.setApplicationRestrictions(
                admin, Config.targetBrowser,
                Bundle().apply { putStringArray(FORCELIST_KEY, store.extensions.toTypedArray()) },
            )
        }
        lines += "Extensions forcées : ${store.extensions.size}"

        // Auto-protection + fermeture des portes de sortie.
        runCatching { dpm.setUninstallBlocked(admin, pkg, true) }
        // Démarrage « sans échec » interdit : sinon un reboot en safe mode désactive
        // l'app (et plus tard le pare-feu), ouvrant une fenêtre sans aucun contrôle.
        // NB : on n'ajoute PAS DISALLOW_DEBUGGING_FEATURES — ce serait couper adb,
        // donc la seule sortie de secours voulue.
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }

        scheduleNext(context, nextWake)
        if (nextWake != null) {
            val mins = ((nextWake - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
            lines += "Prochaine échéance dans ~$mins min"
        }

        return PolicyReport(true, "✅ Device Owner actif.\n\n" + lines.joinToString("\n"))
    }

    /** Programme (ou annule) un réveil inexact pour appliquer la prochaine attente échue. */
    private fun scheduleNext(context: Context, at: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, ALARM_ID,
            Intent(context, TickReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) am.cancel(pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) // inexact : pas de permission, et non contournable à la minute
    }
}
