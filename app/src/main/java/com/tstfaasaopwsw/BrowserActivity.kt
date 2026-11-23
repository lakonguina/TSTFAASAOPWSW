package com.tstfaasaopwsw

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.mozilla.geckoview.GeckoRuntime

/**
 * Navigateur embarqué (GeckoView), imposé comme navigateur par défaut. Un filtre consulte
 * [Store] à chaque navigation et refuse les URL contenant un motif bloqué (voir [BrowserTab]).
 * La boucle d'UI (onglets, overlays) vit dans [Browser].
 */
class BrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent?.dataString?.takeIf { it.isNotBlank() }
        setContent {
            ForcelistTheme {
                Surface(Modifier.fillMaxSize()) {
                    Browser(runtime(this), Store.get(this), start, onExit = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate() // nouvelle intention VIEW : recharge le navigateur sur cette URL
    }

    companion object {
        const val HOME = "https://www.google.com"

        // Un seul GeckoRuntime par processus (lourd), partagé par toutes les sessions.
        @Volatile private var runtime: GeckoRuntime? = null
        fun runtime(ctx: Context): GeckoRuntime =
            runtime ?: synchronized(this) {
                runtime ?: GeckoRuntime.create(ctx.applicationContext).also { runtime = it }
            }

        /** Barre d'adresse : URL telle quelle, sinon complétée, sinon recherche. */
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
