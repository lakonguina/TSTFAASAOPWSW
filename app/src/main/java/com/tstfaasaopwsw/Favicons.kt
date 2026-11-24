package com.tstfaasaopwsw

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The real logo of a service, taken from the site itself (its favicon) and cached on
 * disk. No third-party logo service: we fetch `https://<domain>/favicon.ico` directly, so
 * nothing about the user leaves for anyone but the site they are about to filter.
 */
object Favicons {

    /** Cached favicon for [domain] as a bitmap, downloading it once, or null if unavailable. */
    suspend fun of(context: Context, domain: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = File(cacheDir(context), "${domain.replace('/', '_')}.ico")
        val bytes = if (file.exists() && file.length() > 0) file.readBytes()
        else Downloads.getBytes("https://$domain/favicon.ico")?.also { file.writeBytes(it) }
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "favicons").apply { mkdirs() }
}
