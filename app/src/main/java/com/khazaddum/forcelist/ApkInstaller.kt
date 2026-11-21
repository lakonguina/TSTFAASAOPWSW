package com.khazaddum.forcelist

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Installe un APK via [PackageInstaller]. Device Owner : l'installation est silencieuse,
 * sans aucun dialogue. Le résultat est renvoyé à [UpdateReceiver].
 */
object ApkInstaller {

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            session.commit(resultSender(context, sessionId))
        }
    }

    private fun resultSender(context: Context, sessionId: Int): android.content.IntentSender {
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            context, sessionId,
            Intent(context, UpdateReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
        return pending.intentSender
    }
}
