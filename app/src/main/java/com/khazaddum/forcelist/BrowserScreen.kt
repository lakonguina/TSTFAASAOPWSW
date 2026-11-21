package com.khazaddum.forcelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoView

internal enum class Screen { Browser, Tabs, History }

/** Boucle d'UI du navigateur : onglets, barre d'outils, progression et overlays. */
@Composable
internal fun Browser(runtime: GeckoRuntime, store: Store, start: String?, onExit: () -> Unit) {
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
