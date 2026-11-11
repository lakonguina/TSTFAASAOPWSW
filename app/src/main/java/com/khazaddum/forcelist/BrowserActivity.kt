package com.khazaddum.forcelist

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Le navigateur embarqué (GeckoView), imposé comme navigateur par défaut. Onglets,
 * retour/avance, rechargement, barre d'adresse vivante, progression, historique. Un
 * filtre consulte [Store] à chaque navigation et refuse les URL contenant un motif
 * bloqué (blocage par chemin/URL, complément du pare-feu réseau par domaine).
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

    companion object {
        const val HOME = "https://duckduckgo.com"

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
                else -> "https://duckduckgo.com/?q=" + Uri.encode(s)
            }
        }
    }
}

/** Un onglet : une session GeckoView + son état observable par l'UI. */
private class Tab(runtime: GeckoRuntime, store: Store) {
    val session = GeckoSession()
    var url by mutableStateOf(BrowserActivity.HOME)
    var title by mutableStateOf("")
    var progress by mutableStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                locationUri: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                url = locationUri ?: ""
                locationUri?.let { store.addHistory(it) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@Tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                this@Tab.canGoForward = canGoForward
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val blocked = store.blockedWeb.any { request.uri.contains(it, ignoreCase = true) }
                return GeckoResult.fromValue(if (blocked) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                this@Tab.progress = progress
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                this@Tab.title = title ?: ""
            }
        }
        session.open(runtime)
    }

    fun load(input: String) = session.loadUri(BrowserActivity.normalize(input))
    fun close() = session.close()
}

private enum class Screen { Browser, Tabs, History }

@Composable
private fun Browser(runtime: GeckoRuntime, store: Store, start: String?, onExit: () -> Unit) {
    val tabs = remember { mutableStateListOf<Tab>() }
    var current by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.Browser) }

    // Premier onglet, créé une fois ; sessions fermées à la sortie.
    DisposableEffect(Unit) {
        tabs.add(Tab(runtime, store).also { it.load(start ?: BrowserActivity.HOME) })
        onDispose { tabs.forEach { it.close() } }
    }
    if (tabs.isEmpty()) return
    current = current.coerceIn(0, tabs.lastIndex)
    val tab = tabs[current]

    fun openTab() {
        tabs.add(Tab(runtime, store).also { it.load(BrowserActivity.HOME) })
        current = tabs.lastIndex
        screen = Screen.Browser
    }

    fun closeTab(i: Int) {
        tabs[i].close()
        tabs.removeAt(i)
        if (tabs.isEmpty()) { onExit(); return }
        current = current.coerceIn(0, tabs.lastIndex)
    }

    BackHandler {
        when {
            screen != Screen.Browser -> screen = Screen.Browser
            tab.canGoBack -> tab.session.goBack()
            else -> onExit()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Toolbar(
            tab = tab,
            tabCount = tabs.size,
            onBack = { tab.session.goBack() },
            onForward = { tab.session.goForward() },
            onReload = { tab.session.reload() },
            onGo = { tab.load(it) },
            onTabs = { screen = Screen.Tabs },
            onHistory = { screen = Screen.History },
        )
        if (tab.progress in 1..99) {
            LinearProgressIndicator(progress = tab.progress / 100f, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { GeckoView(it) },
                update = { gv -> if (gv.session != tab.session) { gv.releaseSession(); gv.setSession(tab.session) } },
                modifier = Modifier.fillMaxSize(),
            )
            when (screen) {
                Screen.Tabs -> TabsOverlay(
                    tabs = tabs, current = current,
                    onSelect = { current = it; screen = Screen.Browser },
                    onClose = { closeTab(it) },
                    onNew = { openTab() },
                )
                Screen.History -> HistoryOverlay(
                    store = store,
                    onOpen = { tab.load(it); screen = Screen.Browser },
                    onClose = { screen = Screen.Browser },
                )
                Screen.Browser -> Unit
            }
        }
    }
}

@Composable
private fun Toolbar(
    tab: Tab,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onGo: (String) -> Unit,
    onTabs: () -> Unit,
    onHistory: () -> Unit,
) {
    var text by remember(tab, tab.url) { mutableStateOf(tab.url) }
    Column {
        Row(Modifier.fillMaxWidth().padding(4.dp, 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = tab.canGoBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
            IconButton(onClick = onForward, enabled = tab.canGoForward) { Icon(Icons.Filled.ArrowForward, "Avancer") }
            IconButton(onClick = onReload) { Icon(Icons.Filled.Refresh, "Recharger") }
            TextButton(onClick = onTabs) { Text("$tabCount") }
            IconButton(onClick = onHistory) { Icon(Icons.Filled.List, "Historique") }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp, 0.dp, 8.dp, 6.dp),
            singleLine = true,
            placeholder = { Text("Adresse ou recherche") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo(text) }),
        )
    }
}

@Composable
private fun TabsOverlay(
    tabs: List<Tab>,
    current: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Onglets", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onNew) { Icon(Icons.Filled.Add, "Nouvel onglet") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tabs) { i, t ->
                    ListItem(
                        headlineContent = {
                            Text(t.title.ifEmpty { t.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(t.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { if (i == current) Text("•") },
                        trailingContent = { IconButton(onClick = { onClose(i) }) { Icon(Icons.Filled.Clear, "Fermer") } },
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryOverlay(store: Store, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val entries = remember { store.history() }
    Surface(Modifier.fillMaxSize()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Historique", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = { store.clearHistory(); onClose() }) { Text("Effacer") }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Clear, "Fermer") }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries) { url ->
                    ListItem(
                        headlineContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(url) },
                    )
                }
            }
        }
    }
}
