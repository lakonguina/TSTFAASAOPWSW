package com.khazaddum.forcelist

import android.content.Context
import org.json.JSONObject

/**
 * Mise à jour automatique depuis les Releases GitHub, sans USB. Device Owner, donc
 * [ApkInstaller] installe en silence. On interroge `releases/latest` : si le tag `vN`
 * dépasse [BuildConfig.VERSION_CODE], on télécharge l'APK joint et on l'installe.
 *
 * Mise à jour self : l'APK distant DOIT être signé avec la même clé que l'app installée,
 * sinon Android refuse. D'où une première installation release via USB, puis GitHub.
 */
object Updater {

    private const val PREF = "updater"
    private const val K_LAST = "lastCheck"
    private const val MIN_INTERVAL_MS = 6 * 3_600_000L // au plus une vérif / 6 h

    /** Lance une vérification en tâche de fond ; silencieuse en cas d'erreur réseau. */
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

    /** (versionCode, url de l'APK) de la dernière release, ou null. */
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
