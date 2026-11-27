package com.tstfaasaopwsw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wakes reconciliation: a due delay (alarm) or a phone reboot. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PolicyManager.reconcile(context)
        Updater.checkInBackground(context) // checks GitHub at boot and on each due time
        ImportedLists.refreshInBackground(context) // re-pulls imported blocklists (<=1/day each)
    }
}
