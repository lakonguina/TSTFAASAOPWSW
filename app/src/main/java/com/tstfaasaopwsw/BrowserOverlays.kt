package com.tstfaasaopwsw

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared full-screen frame for the browser's Tabs and History screens: gradient + insets. */
@Composable
private fun OverlayScaffold(title: String, action: (@Composable () -> Unit)?, body: @Composable (PaddingValues) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val insets = GlassContentInsets
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scheme.surface, scheme.surfaceVariant)))) {
            Column(Modifier.fillMaxSize()) {
                GlassLargeTitle(title, Modifier.padding(top = insets.calculateTopPadding())) { action?.invoke() }
                body(PaddingValues(bottom = insets.calculateBottomPadding()))
            }
        }
    }
}

/** Grid of open tabs, each a square preview (last snapshot): select, close, new tab. */
@Composable
internal fun TabsOverlay(
    tabs: List<Tab>,
    current: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
) {
    OverlayScaffold("Tabs", action = { GlassIconButton(onNew, Icons.Filled.Add, "New tab") }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = Space.lg),
            contentPadding = pad,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            itemsIndexed(tabs) { i, t ->
                TabCard(t, selected = i == current, onSelect = { onSelect(i) }, onClose = { onClose(i) })
            }
        }
    }
}

@Composable
private fun TabCard(tab: Tab, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.12f),
                GlassShape.card,
            )
            .clip(GlassShape.card)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .pressable(GlassShape.card, onClick = onSelect),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val bmp = tab.thumbnail
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            } else {
                Box(Modifier.fillMaxSize().background(scheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(
                        UrlRules.host(tab.url).ifEmpty { "New tab" },
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(Space.xs)
                    .clip(GlassShape.pill).background(scheme.scrim.copy(alpha = 0.45f))
                    .pressable(GlassShape.pill, bounded = false, onClick = onClose)
                    .padding(Space.xxs),
            ) {
                Icon(Icons.Filled.Clear, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            tab.title.ifEmpty { tab.url },
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Space.sm, Space.xs, Space.sm, Space.sm),
        )
    }
}

/** Files the browser downloaded: tap opens the system Downloads, or clear the list. */
@Composable
internal fun DownloadsOverlay(store: Store, context: android.content.Context) {
    val entries = remember { store.downloads() }
    OverlayScaffold(
        "Downloads",
        action = { GlassIconButton({ store.clearDownloads() }, Icons.Filled.Delete, "Clear") },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.lg),
            contentPadding = pad,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            items(entries) { e ->
                GlassCard(Modifier.fillMaxWidth().pressable(GlassShape.card) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) {
                    Column {
                        Text(e.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            e.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Saved bookmarks: open an entry, or remove one. */
@Composable
internal fun BookmarksOverlay(store: Store, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val entries = remember { store.bookmarks().toMutableStateList() }
    OverlayScaffold("Bookmarks", action = null) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.lg),
            contentPadding = pad,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            items(entries, key = { it.url }) { e ->
                GlassCard(Modifier.fillMaxWidth().pressable(GlassShape.card) { onOpen(e.url) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.title.ifEmpty { e.url },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                e.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        GlassIconButton({ store.removeBookmark(e.url); entries.remove(e) }, Icons.Filled.Delete, "Remove")
                    }
                }
            }
        }
    }
}

/** Browsing history: open an entry or clear everything. */
@Composable
internal fun HistoryOverlay(store: Store, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val entries = remember { store.history() }
    OverlayScaffold(
        "History",
        action = { GlassIconButton({ store.clearHistory(); onClose() }, Icons.Filled.Delete, "Clear") },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.lg),
            contentPadding = pad,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            items(entries) { url ->
                GlassCard(Modifier.fillMaxWidth().pressable(GlassShape.card) { onOpen(url) }) {
                    Text(url, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
