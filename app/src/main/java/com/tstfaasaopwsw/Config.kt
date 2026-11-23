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

    /** One hideable element: an XPath to hide on [site], labeled [name] in the UI. */
    data class ElementRule(val site: String, val name: String, val xpath: String) {
        /** Stable id used as the enabled-set key and the toggle key. */
        val id: String get() = "$site|$name"
    }

    /**
     * Built-in catalog of hideable elements (mirrors xpath-blocklist.xlsx). Each can be
     * toggled on/off in the Web tab; enabled ones are hidden by the browser, no reload.
     */
    val elementCatalog: List<ElementRule> = listOf(
        ElementRule("x.com", "Feed", "/html/body/div[1]/div/div/div[2]/main/div/div/div/div/div/div[3]"),
        ElementRule("x.com", "Right sidebar", "//div[@data-testid='sidebarColumn']"),
        ElementRule("youtube.com", "Home grid", "//ytd-rich-grid-renderer"),
    )
}
