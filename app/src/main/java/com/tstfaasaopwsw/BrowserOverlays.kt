package com.tstfaasaopwsw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Liste des onglets ouverts : sélection, fermeture, nouvel onglet. */
@Composable
internal fun TabsOverlay(
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

/** Historique de navigation : ouvrir une entrée ou tout effacer. */
@Composable
internal fun HistoryOverlay(store: Store, onOpen: (String) -> Unit, onClose: () -> Unit) {
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
