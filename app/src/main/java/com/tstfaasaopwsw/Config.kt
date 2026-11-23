package com.tstfaasaopwsw

/** Fixed constants. The lists (blocked apps, delay) are dynamic and live in
 *  [Store], editable from the app. */
object Config {

    /**
     * PUBLIC GitHub repo the app auto-updates from (OWNER/REPO). The updater reads
     * `releases/latest` and installs the APK when tag `vN` is newer than
     * [BuildConfig.VERSION_CODE]. Change this if the repo moves.
     */
    const val githubRepo = "lakonguina/TSTFAASAOPWSW"

    /** Quick choices for the delay picker (minutes); a free field allows any other value. */
    val delayChoicesMinutes: List<Long> = listOf(0, 5, 10, 20, 30, 60, 120, 360, 720, 1440)

    fun formatDelay(min: Long): String = when {
        min <= 0L -> "none"
        min == 1L -> "1 min (test)"
        min < 60L -> "$min min"
        min % 60L == 0L -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60}"
    }
}
