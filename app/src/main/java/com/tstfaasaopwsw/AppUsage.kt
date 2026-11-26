package com.tstfaasaopwsw

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

/**
 * System-measured foreground time per app, for the `app:` minutes quota. Reads
 * [UsageStatsManager], which needs the one-time "Usage access" grant (see [hasPermission]);
 * without it this reports 0, so an app quota simply does not enforce until granted.
 */
object AppUsage {

    fun hasPermission(context: Context): Boolean {
        val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = AppOpsManager.OPSTR_GET_USAGE_STATS
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            aom.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            aom.checkOpNoThrow(op, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground seconds today for [pkg]; 0 when the grant is missing or there is no data. */
    fun foregroundSecondsToday(context: Context, pkg: String): Long {
        if (!hasPermission(context)) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDayMillis(), System.currentTimeMillis())
            ?: return 0L
        return stats.filter { it.packageName == pkg }.sumOf { it.totalTimeInForeground } / 1000L
    }

    private fun startOfDayMillis(): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
}
