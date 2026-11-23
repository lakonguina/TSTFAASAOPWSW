package com.tstfaasaopwsw

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Raw network access for the [Updater]: read text, download the APK. */
object Downloads {

    private const val TIMEOUT_MS = 15_000

    /** Body of a GET request as text, or null on failure / non-2xx HTTP. */
    fun getText(url: String): String? = runCatching {
        open(url).run {
            setRequestProperty("Accept", "application/vnd.github+json")
            if (responseCode !in 200..299) return null
            inputStream.bufferedReader().use { it.readText() }
        }
    }.getOrNull()

    /** Downloads [url] to an internal file and returns the file, or null. */
    fun toApk(context: Context, url: String): File? = runCatching {
        val out = File(context.filesDir, "update.apk")
        open(url).run {
            if (responseCode !in 200..299) return null
            inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        out
    }.getOrNull()

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true // follows GitHub redirect -> objects.githubusercontent.com
        }
}
