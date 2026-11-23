package com.tstfaasaopwsw

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

    /**
     * One hideable element, backed by a built-in web extension [extId] that hides a CSS
     * selector on [site] (static content-script CSS). [name] labels the UI toggle.
     */
    data class ElementRule(val site: String, val name: String, val extId: String) {
        /** Stable id used as the enabled-set key and the toggle key. */
        val id: String get() = "$site|$name"
        val geckoId: String get() = "$extId@tstfaasaopwsw"
        val uri: String get() = "resource://android/assets/rules/$extId/"
    }

    /**
     * Built-in catalog of hideable elements (mirrors xpath-blocklist.xlsx). Each is a tiny
     * bundled extension (assets/rules/<extId>); the Web-tab toggle enables or disables it.
     */
    val elementCatalog: List<ElementRule> = listOf(
        ElementRule("x.com", "Feed", "feedx"),
        ElementRule("x.com", "Right sidebar", "sidebarx"),
        ElementRule("youtube.com", "Home feed", "ytfeed"),
    )
}
