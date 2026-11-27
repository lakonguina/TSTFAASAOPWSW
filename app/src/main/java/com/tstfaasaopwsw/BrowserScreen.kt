package com.tstfaasaopwsw

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoView

internal enum class Screen { Browser, Tabs, History, Bookmarks, Downloads }

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
    var barHeight by remember { mutableIntStateOf(0) } // measured glass-bar height, for content inset

    // Snapshots the live tab (the one the GeckoView currently shows) into its own thumbnail.
    // Called before switching away, so every tab — not just the one open when the grid is
    // shown — carries a fresh preview in the Tabs grid.
    fun snapshotLive() {
        val v = viewRef.value ?: return
        val t = tabs.getOrNull(current) ?: return
        runCatching { v.capturePixels().accept { bmp -> if (bmp != null) t.thumbnail = bmp } }
    }

    // Enqueues a download and records it (see the Downloads screen).
    fun doDownload(url: String, name: String) {
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(
                android.app.DownloadManager.Request(android.net.Uri.parse(url))
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED),
            )
        }
        store.addDownload(url, name)
    }

    // Refs so a tab's delegates (set at construction) can reach the current list/handlers
    // without a recursion cycle.
    val onNewTabRef = remember { mutableStateOf<(org.mozilla.geckoview.GeckoSession) -> Unit>({}) }
    val onDownloadRef = remember { mutableStateOf<(String, String) -> Unit>({ _, _ -> }) }
    onDownloadRef.value = { u, n -> doDownload(u, n) }
    fun newTab(url: String?): Tab =
        Tab(runtime, store, onNewTab = { onNewTabRef.value(it) }, onDownload = { u, n -> onDownloadRef.value(u, n) })
            .also { if (url != null) it.load(url) }
    onNewTabRef.value = { s ->
        snapshotLive() // preview the tab we're leaving before the new one takes focus
        tabs.add(Tab(runtime, store, existing = s, onNewTab = { onNewTabRef.value(it) }, onDownload = { u, n -> onDownloadRef.value(u, n) }))
        current = tabs.lastIndex
        screen = Screen.Browser
    }

    DisposableEffect(Unit) { // restores persisted tabs; sessions closed on exit
        store.savedTabs().ifEmpty { listOf(BrowserActivity.HOME) }
            .forEach { u -> tabs.add(newTab(u)) }
        current = store.savedTabIndex().coerceIn(0, tabs.lastIndex)
        if (start != null) { // external link: open it in a new tab
            tabs.add(newTab(start)); current = tabs.lastIndex
        }
        onDispose { tabs.forEach { it.close() } }
    }
    if (tabs.isEmpty()) return

    LaunchedEffect(Unit) {
        snapshotFlow { tabs.map { it.url } to current }.collect { (urls, cur) -> store.saveTabs(urls, cur) }
    }
    current = current.coerceIn(0, tabs.lastIndex)
    val tab = tabs[current]

    // Tints the status strip with the page's top colour, like iOS Safari. Samples a whole
    // horizontal band near the top and takes its average, so one stray pixel (an icon, an
    // image edge) no longer decides the colour. Runs only on load-complete, never per scroll
    // frame — the strip colour then stays fixed for the page, as in Safari.
    fun sampleTop() {
        val v = viewRef.value ?: return
        runCatching {
            v.capturePixels().accept { bmp ->
                if (bmp != null && bmp.width > 2 && bmp.height > 6) {
                    val y = 3
                    var r = 0L; var g = 0L; var b = 0L; var n = 0
                    var x = 2
                    while (x < bmp.width - 2) {
                        val px = bmp.getPixel(x, y); r += (px shr 16) and 0xFF
                        g += (px shr 8) and 0xFF; b += px and 0xFF; n++; x += 8
                    }
                    if (n > 0) topColor = Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
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

    // Quota metering: while the app is in front and a page is shown, count one second against
    // that page's web/feed quota, then block the page (or hide its feed, via a reload) the
    // instant a per-rule daily limit is reached. App quotas are metered by the system instead.
    var resumed by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val t = tabs.getOrNull(current) ?: continue
            if (!resumed || screen != Screen.Browser || t.blocked != null) continue
            val url = t.url
            val hidden = store.tickUsage(url)
            if (store.effectiveBlockedWeb().any { UrlRules.matchesUrl(url, it) }) t.blocked = url
            else if (hidden.isNotEmpty()) t.session.reload() // feed quota spent -> hide it now
        }
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
        snapshotLive() // preview the tab we're leaving
        tabs.add(newTab(BrowserActivity.HOME))
        current = tabs.lastIndex
        screen = Screen.Browser
    }

    fun closeTab(i: Int) {
        tabs[i].close(); tabs.removeAt(i)
        if (tabs.isEmpty()) { onExit(); return }
        current = current.coerceIn(0, tabs.lastIndex)
    }

    var finding by remember { mutableStateOf(false) }

    fun share(text: String) = runCatching {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
        context.startActivity(android.content.Intent.createChooser(send, null)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
            ?.setPrimaryClip(android.content.ClipData.newPlainText("url", text))
    }

    BackHandler {
        when {
            finding -> { tab.clearFind(); finding = false }
            tab.contextTarget != null -> tab.contextTarget = null
            screen != Screen.Browser -> screen = Screen.Browser
            tab.blocked != null -> tab.blocked = null
            tab.canGoBack -> tab.session.goBack()
            else -> onExit()
        }
    }

    val density = LocalDensity.current
    // Content bottom inset so the glass bar never covers the site's own bottom UI (e.g.
    // Twitter's nav), the way Safari insets the page above its toolbar. Animated so the page
    // reflows smoothly as the bar auto-hides.
    val bottomInset by animateDpAsState(
        if (barsShown) with(density) { barHeight.toDp() } else 0.dp, label = "bottomInset",
    )

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        // Capture the page into [backdrop] (for the glass bar's refraction) ONLY while a bar
        // is shown. During a scroll the bar is hidden, so we skip the per-frame layer capture
        // entirely — the main cost of the liquid-glass browser.
        Box(Modifier.fillMaxSize().then(if (barsShown) Modifier.layerBackdrop(backdrop) else Modifier)) {
            AndroidView(
                // TextureView backend: draws in the view hierarchy, so Compose captures the
                // page into [backdrop] (glass refraction) and we can sample its colour.
                factory = { GeckoView(it).apply { setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW); viewRef.value = this } },
                update = { gv -> if (gv.session != tab.session) { gv.releaseSession(); gv.setSession(tab.session) } },
                // Page sits below the status strip and above the glass bar, like Safari.
                modifier = Modifier.fillMaxSize().padding(top = statusBarH, bottom = bottomInset),
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
                Screen.Bookmarks -> BookmarksOverlay(store, { tab.load(it); screen = Screen.Browser }, { screen = Screen.Browser })
                Screen.Downloads -> DownloadsOverlay(store, context)
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
                Box(Modifier.onSizeChanged { barHeight = it.height }) {
                    if (finding) {
                        FindBar(
                            onFind = { q, fwd -> tab.find(q, fwd) },
                            onClose = { tab.clearFind(); finding = false },
                        )
                    } else {
                        BrowserBottomBar(
                            tab = tab, tabCount = tabs.size,
                            history = store.history(),
                            isBookmarked = store.isBookmarked(tab.url),
                            onBack = { tab.session.goBack() },
                            onForward = { tab.session.goForward() },
                            onReload = { tab.session.reload() },
                            onGo = { tab.load(it) },
                            onTabs = { snapshotLive(); screen = Screen.Tabs },
                            onHistory = { screen = Screen.History },
                            onBookmarks = { screen = Screen.Bookmarks },
                            onDownloads = { screen = Screen.Downloads },
                            onToggleBookmark = {
                                if (store.isBookmarked(tab.url)) store.removeBookmark(tab.url)
                                else store.addBookmark(tab.url, tab.title)
                            },
                            onShare = { share(tab.url) },
                            onCopyLink = { copy(tab.url) },
                            onFind = { finding = true },
                            onToggleDesktop = { tab.setDesktopMode(!tab.desktop) },
                            onSettings = onSettings,
                        )
                    }
                }
            }
        }

        // Long-press context menu on a link/image.
        tab.contextTarget?.let { target ->
            ContextMenuSheet(
                target = target,
                onDismiss = { tab.contextTarget = null },
                onOpenNewTab = { url -> snapshotLive(); tabs.add(newTab(url)); current = tabs.lastIndex; screen = Screen.Browser },
                onCopy = { copy(it) },
                onShare = { share(it) },
                onDownload = { doDownload(it, android.net.Uri.parse(it).lastPathSegment ?: "image") },
            )
        }
    }
}
