package com.khazaddum.forcelist

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Le navigateur embarqué (GeckoView). C'est lui que l'app impose comme navigateur par
 * défaut : tout lien web atterrit ici. Un unique filtre consulte [Store] à chaque
 * navigation et refuse les URL contenant un motif bloqué — le blocage par chemin/URL,
 * que le pare-feu réseau (par domaine) ne peut pas faire.
 */
class BrowserActivity : ComponentActivity() {

    private lateinit var session: GeckoSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = GeckoSession().apply {
            navigationDelegate = WebFilter(Store.get(this@BrowserActivity))
            open(runtime(this@BrowserActivity))
        }
        session.loadUri(intent?.dataString?.takeIf { it.isNotBlank() } ?: HOME)

        setContent {
            ForcelistTheme {
                BrowserScreen(session) { typed -> session.loadUri(normalize(typed)) }
            }
        }
    }

    override fun onDestroy() {
        if (::session.isInitialized) session.close()
        super.onDestroy()
    }

    companion object {
        private const val HOME = "https://duckduckgo.com"

        // Un seul GeckoRuntime par processus (lourd), partagé par toutes les sessions.
        @Volatile private var runtime: GeckoRuntime? = null
        fun runtime(ctx: Context): GeckoRuntime =
            runtime ?: synchronized(this) {
                runtime ?: GeckoRuntime.create(ctx.applicationContext).also { runtime = it }
            }

        /** Barre d'adresse : URL telle quelle, sinon complétée, sinon recherche. */
        private fun normalize(input: String): String {
            val s = input.trim()
            return when {
                s.contains("://") || s.startsWith("about:") -> s
                s.contains('.') && !s.contains(' ') -> "https://$s"
                else -> "https://duckduckgo.com/?q=" + Uri.encode(s)
            }
        }
    }
}

/** Refuse toute URL contenant un motif bloqué. Relit [Store] en direct à chaque requête. */
private class WebFilter(private val store: Store) : GeckoSession.NavigationDelegate {
    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
    ): GeckoResult<AllowOrDeny> {
        val blocked = store.blockedWeb.any { request.uri.contains(it, ignoreCase = true) }
        return GeckoResult.fromValue(if (blocked) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
    }
}

@Composable
private fun BrowserScreen(session: GeckoSession, onGo: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            placeholder = { Text("Adresse ou recherche") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo(url) }),
        )
        AndroidView(
            factory = { ctx -> GeckoView(ctx).apply { setSession(session) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
