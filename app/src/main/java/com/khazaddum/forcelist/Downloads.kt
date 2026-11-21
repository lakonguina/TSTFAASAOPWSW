package com.khazaddum.forcelist

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Accès réseau brut de l'[Updater] : lire du texte, télécharger l'APK. */
object Downloads {

    private const val TIMEOUT_MS = 15_000

    /** Corps d'une requête GET en texte, ou null si échec/HTTP non-2xx. */
    fun getText(url: String): String? = runCatching {
        open(url).run {
            setRequestProperty("Accept", "application/vnd.github+json")
            if (responseCode !in 200..299) return null
            inputStream.bufferedReader().use { it.readText() }
        }
    }.getOrNull()

    /** Télécharge [url] vers un fichier interne et renvoie le fichier, ou null. */
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
            instanceFollowRedirects = true // suit la redirection GitHub -> objects.githubusercontent.com
        }
}
