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

    /**
     * A service groups, under one name, the app [packages] and the website [domain] of
     * the same product (e.g. Twitter: com.twitter.android + x.com). The services list is
     * built from this catalog so a service shows even when its app is not installed,
     * letting its web rules be set in advance. [domain] null = app with no known site.
     */
    data class Service(val label: String, val packages: List<String>, val domain: String?)

    val catalog: List<Service> = listOf(
        Service("Google", listOf("com.google.android.googlequicksearchbox"), "google.com"),
        Service("YouTube", listOf("com.google.android.youtube"), "youtube.com"),
        Service("Instagram", listOf("com.instagram.android"), "instagram.com"),
        Service("Twitter / X", listOf("com.twitter.android"), "x.com"),
        Service("TikTok", listOf("com.zhiliaoapp.musically"), "tiktok.com"),
        Service("Facebook", listOf("com.facebook.katana"), "facebook.com"),
        Service("Reddit", listOf("com.reddit.frontpage"), "reddit.com"),
        Service("Snapchat", listOf("com.snapchat.android"), "snapchat.com"),
    )

    /** Hideable surfaces (feed, navbar…) bound to a given site domain. */
    fun elementsFor(domain: String): List<ElementRule> = elementCatalog.filter { it.site == domain }
}
