package com.tstfaasaopwsw

/** "in 2 h 5 min" / "in 9 min" / "in less than 1 min". */
internal fun countdown(at: Long): String {
    val totalMin = ((at - System.currentTimeMillis()) / 60_000L)
    return when {
        totalMin <= 0L -> "in less than 1 min"
        totalMin < 60L -> "in $totalMin min"
        totalMin % 60L == 0L -> "in ${totalMin / 60} h"
        else -> "in ${totalMin / 60} h ${totalMin % 60} min"
    }
}
