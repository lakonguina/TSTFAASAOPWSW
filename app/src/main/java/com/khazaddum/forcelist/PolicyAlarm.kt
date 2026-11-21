package com.khazaddum.forcelist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Réveil inexact qui applique la prochaine attente échue de [Store]. */
object PolicyAlarm {

    const val ACTION_TICK = "com.khazaddum.forcelist.TICK"
    private const val ALARM_ID = 1001

    /** Programme un réveil à [at], ou l'annule si [at] est null. */
    fun schedule(context: Context, at: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, ALARM_ID,
            Intent(context, TickReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) am.cancel(pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
