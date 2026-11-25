package com.tstfaasaopwsw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** "in 2 h 05 min 09 s" / "in 9 min 03 s" / "in 42 s" — down to the second. */
internal fun formatCountdown(remainingMs: Long): String {
    val s = (remainingMs / 1000L).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "in %d h %02d min %02d s".format(h, m, sec)
        m > 0 -> "in %d min %02d s".format(m, sec)
        else -> "in %d s".format(sec)
    }
}

/**
 * A live countdown to [at] (epoch millis), re-read every second so the display ticks. Use in
 * a composable: `Text("Unblock " + countdownLive(at))`.
 */
@Composable
internal fun countdownLive(at: Long): String {
    val now by produceState(System.currentTimeMillis(), at) {
        while (true) { value = System.currentTimeMillis(); delay(1000L) }
    }
    return formatCountdown(at - now)
}
