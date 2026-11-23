package com.tstfaasaopwsw

import android.content.Context
import org.json.JSONObject

/**
 * Automatic update from GitHub Releases, no USB. As Device Owner, [ApkInstaller] installs
 * silently. We query `releases/latest`: if tag `vN` is newer than [BuildConfig.VERSION_CODE],
 * we download the attached APK and install it.
 *
 * Self-update: the remote APK MUST be signed with the same key as the installed app,
 * otherwise Android refuses it. Hence a first release install over USB, then GitHub.
 */
object Updater {

    private const val PREF = "updater"
    private const val K_LAST = "lastCheck"
    private const val MIN_INTERVAL_MS = 6 * 3_600_000L // at most one check / 6 h

    /** Starts a background check; silent on network error. */
    fun checkInBackground(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { check(app) } }.apply { isDaemon = true }.start()
    }

    private fun check(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong(K_LAST, 0L) < MIN_INTERVAL_MS) return
        sp.edit().putLong(K_LAST, now).apply()

        val (code, url) = latestRelease() ?: return
        if (code <= BuildConfig.VERSION_CODE) return
        val apk = Downloads.toApk(context, url) ?: return
        ApkInstaller.install(context, apk)
    }

    /** (versionCode, APK url) of the latest release, or null. */
    private fun latestRelease(): Pair<Int, String>? {
        val body = Downloads.getText("https://api.github.com/repos/${Config.githubRepo}/releases/latest")
            ?: return null
        val obj = JSONObject(body)
        val code = obj.getString("tag_name").removePrefix("v").toIntOrNull() ?: return null
        val assets = obj.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) return code to a.getString("browser_download_url")
        }
        return null
    }
}
