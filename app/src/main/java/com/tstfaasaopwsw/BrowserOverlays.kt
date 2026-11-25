package com.tstfaasaopwsw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow

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

/** List of open tabs: select, close, new tab. */
@Composable
internal fun TabsOverlay(
    tabs: List<Tab>,
    current: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
) {
    OverlayScaffold("Tabs", action = { GlassIconButton(onNew, Icons.Filled.Add, "New tab") }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.lg),
            contentPadding = pad,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            itemsIndexed(tabs) { i, t ->
                GlassCard(Modifier.fillMaxWidth().pressable(GlassShape.card) { onSelect(i) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title.ifEmpty { t.url },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                t.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        GlassIconButton({ onClose(i) }, Icons.Filled.Clear, "Close")
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
