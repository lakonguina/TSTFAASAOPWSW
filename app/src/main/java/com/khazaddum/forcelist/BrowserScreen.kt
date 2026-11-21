package com.khazaddum.forcelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoView

internal enum class Screen { Browser, Tabs, History }

/** Boucle d'UI du navigateur : contenu web, overlays, et barre d'actions en bas. */
@Composable
internal fun Browser(runtime: GeckoRuntime, store: Store, start: String?, onExit: () -> Unit) {
    val tabs = remember { mutableStateListOf<Tab>() }
    var current by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.Browser) }

    DisposableEffect(Unit) { // restaure les onglets persistés ; sessions fermées à la sortie
        store.savedTabs().ifEmpty { listOf(BrowserActivity.HOME) }
            .forEach { u -> tabs.add(Tab(runtime, store).also { it.load(u) }) }
        current = store.savedTabIndex().coerceIn(0, tabs.lastIndex)
        if (start != null) { // lien externe : nouvel onglet dessus
            tabs.add(Tab(runtime, store).also { it.load(start) }); current = tabs.lastIndex
        }
        onDispose { tabs.forEach { it.close() } }
    }
    if (tabs.isEmpty()) return

    // Persiste les URLs ouvertes et l'onglet courant à chaque changement.
    LaunchedEffect(Unit) {
        snapshotFlow { tabs.map { it.url } to current }.collect { (urls, cur) -> store.saveTabs(urls, cur) }
    }
    current = current.coerceIn(0, tabs.lastIndex)
    val tab = tabs[current]

    fun openTab() {
        tabs.add(Tab(runtime, store).also { it.load(BrowserActivity.HOME) })
        current = tabs.lastIndex
        screen = Screen.Browser
    }

    fun closeTab(i: Int) {
        tabs[i].close(); tabs.removeAt(i)
        if (tabs.isEmpty()) { onExit(); return }
        current = current.coerceIn(0, tabs.lastIndex)
    }

    BackHandler {
        when {
            screen != Screen.Browser -> screen = Screen.Browser
            tab.blocked != null -> tab.blocked = null
            tab.canGoBack -> tab.session.goBack()
            else -> onExit()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { GeckoView(it) },
                update = { gv -> if (gv.session != tab.session) { gv.releaseSession(); gv.setSession(tab.session) } },
                modifier = Modifier.fillMaxSize(),
            )
            if (tab.progress in 1..99) {
                LinearProgressIndicator(progress = tab.progress / 100f, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())
            }
            when (screen) {
                Screen.Tabs -> TabsOverlay(tabs, current, { current = it; screen = Screen.Browser }, { closeTab(it) }, { openTab() })
                Screen.History -> HistoryOverlay(store, { tab.load(it); screen = Screen.Browser }, { screen = Screen.Browser })
                Screen.Browser -> tab.blocked?.let { BlockedPage(it) { tab.leaveBlocked() } }
            }
        }
        BrowserBottomBar(
            tab = tab, tabCount = tabs.size,
            onBack = { tab.session.goBack() },
            onForward = { tab.session.goForward() },
            onReload = { tab.session.reload() },
            onGo = { tab.load(it) },
            onTabs = { screen = Screen.Tabs },
            onHistory = { screen = Screen.History },
            onApp = onExit,
        )
    }
}
