package com.tstfaasaopwsw

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
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

/** Browser UI loop: web content, overlays, and the glass bottom bar. */
@Composable
internal fun Browser(
    runtime: GeckoRuntime,
    store: Store,
    start: String?,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val tabs = remember { mutableStateListOf<Tab>() }
    var current by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.Browser) }
    var barsVisible by remember { mutableStateOf(true) }

    DisposableEffect(Unit) { // restores persisted tabs; sessions closed on exit
        store.savedTabs().ifEmpty { listOf(BrowserActivity.HOME) }
            .forEach { u -> tabs.add(Tab(runtime, store).also { it.load(u) }) }
        current = store.savedTabIndex().coerceIn(0, tabs.lastIndex)
        if (start != null) { // external link: open it in a new tab
            tabs.add(Tab(runtime, store).also { it.load(start) }); current = tabs.lastIndex
        }
        onDispose { tabs.forEach { it.close() } }
    }
    if (tabs.isEmpty()) return

    // Persists the open URLs and the current tab on every change.
    LaunchedEffect(Unit) {
        snapshotFlow { tabs.map { it.url } to current }.collect { (urls, cur) -> store.saveTabs(urls, cur) }
    }
    current = current.coerceIn(0, tabs.lastIndex)
    val tab = tabs[current]

    // Safari-style auto-hide: scrolling down past a little threshold tucks the bar away;
    // scrolling up, or reaching the top, brings it back. Overlays always show it.
    LaunchedEffect(tab) {
        var last = tab.scrollY
        snapshotFlow { tab.scrollY }.collect { y ->
            when {
                y <= 0 -> barsVisible = true
                y - last > 16 -> barsVisible = false
                last - y > 16 -> barsVisible = true
            }
            last = y
        }
    }
    val barsShown = barsVisible || screen != Screen.Browser

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

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { GeckoView(it) },
            update = { gv -> if (gv.session != tab.session) { gv.releaseSession(); gv.setSession(tab.session) } },
            modifier = Modifier.fillMaxSize(),
        )
        if (tab.progress in 1..99) {
            LinearProgressIndicator(
                progress = tab.progress / 100f,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            )
        }
        when (screen) {
            Screen.Tabs -> TabsOverlay(tabs, current, { current = it; screen = Screen.Browser }, { closeTab(it) }, { openTab() })
            Screen.History -> HistoryOverlay(store, { tab.load(it); screen = Screen.Browser }, { screen = Screen.Browser })
            Screen.Browser -> tab.blocked?.let { BlockedPage(it) { tab.leaveBlocked() } }
        }
        AnimatedVisibility(
            visible = barsShown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            BrowserBottomBar(
                tab = tab, tabCount = tabs.size,
                onBack = { tab.session.goBack() },
                onForward = { tab.session.goForward() },
                onReload = { tab.session.reload() },
                onGo = { tab.load(it) },
                onTabs = { screen = Screen.Tabs },
                onHistory = { screen = Screen.History },
                onSettings = onSettings,
            )
        }
    }
}
