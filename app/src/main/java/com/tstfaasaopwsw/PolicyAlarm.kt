package com.tstfaasaopwsw

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Inexact wakeup that applies the next due pending action of [Store]. */
object PolicyAlarm {

    const val ACTION_TICK = "com.tstfaasaopwsw.TICK"
    private const val ALARM_ID = 1001

    /** Schedules a wakeup at [at], or cancels it if [at] is null. */
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
