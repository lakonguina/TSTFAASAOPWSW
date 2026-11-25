package com.tstfaasaopwsw

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoView

internal enum class Screen { Browser, Tabs, History }

/** Browser UI loop: web content, a Safari-style tinted status strip, overlays, glass bar. */
@Composable
internal fun Browser(
    runtime: GeckoRuntime,
    store: Store,
    start: String?,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val tabs = remember { mutableStateListOf<Tab>() }
    var current by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.Browser) }
    var barsVisible by remember { mutableStateOf(true) }
    val viewRef = remember { mutableStateOf<GeckoView?>(null) }
    var topColor by remember { mutableStateOf<Color?>(null) }

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

    LaunchedEffect(Unit) {
        snapshotFlow { tabs.map { it.url } to current }.collect { (urls, cur) -> store.saveTabs(urls, cur) }
    }
    current = current.coerceIn(0, tabs.lastIndex)
    val tab = tabs[current]

    // Reads the page's top pixel colour (via the TextureView) and tints the status strip with
    // it, like iOS Safari. Cheap because it only runs on load-complete and after a scroll
    // settles, not per frame.
    fun sampleTop() {
        val v = viewRef.value ?: return
        runCatching {
            v.capturePixels().accept { bmp ->
                if (bmp != null && bmp.width > 0 && bmp.height > 6) {
                    topColor = Color(bmp.getPixel(bmp.width / 2, 4))
                }
            }
        }
    }

    // Safari-style bar auto-hide.
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
    LaunchedEffect(tab) {
        topColor = null
        snapshotFlow { tab.progress }.collectLatest { p -> if (p == 100) { delay(150); sampleTop() } }
    }
    LaunchedEffect(tab) {
        snapshotFlow { tab.scrollY }.collectLatest { delay(250); sampleTop() }
    }
    // Keep the system status-bar icons readable against whatever colour the strip takes.
    LaunchedEffect(topColor) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val light = (topColor ?: Color.Black).luminance() > 0.5f
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = light
    }

    val barsShown = barsVisible || screen != Screen.Browser
    val statusBarH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AndroidView(
                // TextureView backend: draws in the view hierarchy, so Compose captures the
                // page into [backdrop] (glass refraction) and we can sample its colour.
                factory = { GeckoView(it).apply { setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW); viewRef.value = this } },
                update = { gv -> if (gv.session != tab.session) { gv.releaseSession(); gv.setSession(tab.session) } },
                // Page sits just below the status strip (lowered), like Safari.
                modifier = Modifier.fillMaxSize().padding(top = statusBarH),
            )
            if (tab.progress in 1..99) {
                LinearProgressIndicator(
                    progress = tab.progress / 100f,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarH).fillMaxWidth(),
                )
            }
            when (screen) {
                Screen.Tabs -> TabsOverlay(tabs, current, { current = it; screen = Screen.Browser }, { closeTab(it) }, { openTab() })
                Screen.History -> HistoryOverlay(store, { tab.load(it); screen = Screen.Browser }, { screen = Screen.Browser })
                Screen.Browser -> tab.blocked?.let { BlockedPage(it) { tab.leaveBlocked() } }
            }
        }

        // The Safari-style tinted strip behind the system status bar (chrome, not captured).
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusBarH)
                .background(topColor ?: MaterialTheme.colorScheme.surface),
        )

        AnimatedVisibility(
            visible = barsShown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
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
}
