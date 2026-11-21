package com.khazaddum.forcelist

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Onglet Web : ouvrir le navigateur, basculer le pare-feu, gérer les motifs bloqués. */
@Composable
internal fun WebScreen(snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var input by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, BrowserActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
        ) { Text("Ouvrir le navigateur") }

        ToggleRow(
            leading = { WebGlyph() },
            title = "Filtrer tout le téléphone",
            subtitle = if (snap.firewallEnabled) "Actif — les sites bloqués le sont partout"
            else "Inactif — seul le navigateur filtre",
            checked = snap.firewallEnabled,
            pending = null,
            onChange = { on -> FirewallPolicy.setFirewall(context, on); refresh() },
        )

        SectionHeader("Sites et URL bloqués")
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Motif (ex. pornhub.com)") },
                singleLine = true,
            )
            OutlinedButton(onClick = {
                val p = input.trim()
                if (p.isNotEmpty()) { store.blockWeb(p); input = ""; refresh() }
            }) { Text("Bloquer") }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(snap.blockedWeb.sorted(), key = { "web:$it" }) { pattern ->
                val pendingAt = snap.pendingWebRemoval[pattern]
                ToggleRow(
                    leading = { WebGlyph() },
                    title = pattern,
                    subtitle = "Refusé dans le navigateur",
                    checked = pendingAt == null,
                    pending = pendingAt?.let { "Retrait ${countdown(it)}" },
                    onChange = { on -> if (on) store.blockWeb(pattern) else store.unblockWeb(pattern); refresh() },
                )
            }
        }
    }
}
