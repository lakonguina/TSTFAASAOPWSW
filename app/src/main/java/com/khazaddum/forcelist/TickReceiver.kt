package com.khazaddum.forcelist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Réveille la réconciliation : échéance d'un délai (alarme) ou redémarrage du téléphone. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PolicyManager.reconcile(context)
    }
}
