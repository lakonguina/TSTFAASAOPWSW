package com.tstfaasaopwsw

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.mozilla.geckoview.GeckoRuntime

/**
 * Embedded browser (GeckoView), enforced as the default browser. A filter consults
 * [Store] on every navigation and refuses URLs containing a blocked pattern (see [BrowserTab]).
 * The UI loop (tabs, overlays) lives in [Browser].
 */
class BrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Updater.checkInBackground(this) // auto-update from GitHub (throttled)
        ElementHider.install(runtime(this), Store.get(this))
        val start = intent?.dataString?.takeIf { it.isNotBlank() }
        setContent {
            ForcelistTheme {
                Surface(Modifier.fillMaxSize()) {
                    Browser(
                        runtime(this), Store.get(this), start,
                        onSettings = { startActivity(Intent(this, MainActivity::class.java)) },
                        onExit = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate() // new VIEW intent: reloads the browser on this URL
    }

    companion object {
        const val HOME = "https://www.google.com"

        // A single GeckoRuntime per process (heavy), shared by all sessions.
        @Volatile private var runtime: GeckoRuntime? = null
        fun runtime(ctx: Context): GeckoRuntime =
            runtime ?: synchronized(this) {
                runtime ?: GeckoRuntime.create(ctx.applicationContext).also { runtime = it }
            }

        /** Address bar: URL as-is, otherwise completed, otherwise a search. */
        fun normalize(input: String): String {
            val s = input.trim()
            return when {
                s.contains("://") || s.startsWith("about:") -> s
                s.contains('.') && !s.contains(' ') -> "https://$s"
                else -> "https://www.google.com/search?q=" + Uri.encode(s)
            }
        }
    }
}
