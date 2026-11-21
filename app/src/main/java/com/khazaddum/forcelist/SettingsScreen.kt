package com.khazaddum.forcelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Onglet Réglages : statut + délai de réflexion (le sélecteur vit dans [DelayPicker]). */
@Composable
internal fun SettingsScreen(snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { StatusCard(snap) }
        item { SectionHeader("Délai de réflexion") }
        item {
            Text(
                "Actuel : ${Config.formatDelay(snap.delayMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp, 0.dp),
            )
        }
        snap.pendingDelay?.let { (v, at) ->
            item {
                Box(Modifier.padding(16.dp, 2.dp)) {
                    Text(
                        "→ passe à ${Config.formatDelay(v)} ${countdown(at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { DelayPicker(snap.delayMinutes) { store.requestDelay(it); refresh() } }
        item {
            Text(
                "Augmenter le délai (ou bloquer une app, forcer une extension) prend effet " +
                    "tout de suite. Le baisser — ou débloquer, retirer une extension — ne prend " +
                    "effet qu'après le délai courant : en attendant, ça reste verrouillé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 16.dp),
            )
        }
    }
}
