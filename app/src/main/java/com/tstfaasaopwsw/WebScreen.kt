package com.tstfaasaopwsw

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

/** Web tab: toggle the firewall and manage blocked patterns. */
@Composable
internal fun WebScreen(snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var input by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ToggleRow(
            leading = { WebGlyph() },
            title = "Filter the whole phone",
            subtitle = if (snap.firewallEnabled) "On — blocked sites are blocked everywhere"
            else "Off — only the browser filters",
            checked = snap.firewallEnabled,
            pending = null,
            onChange = { on -> FirewallPolicy.setFirewall(context, on); refresh() },
        )

        SectionHeader("Blocked sites and URLs")
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Pattern (e.g. pornhub.com)") },
                singleLine = true,
            )
            OutlinedButton(onClick = {
                val p = input.trim()
                if (p.isNotEmpty()) { store.blockWeb(p); input = ""; refresh() }
            }) { Text("Block") }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(snap.blockedWeb.sorted(), key = { "web:$it" }) { pattern ->
                val pendingAt = snap.pendingWebRemoval[pattern]
                ToggleRow(
                    leading = { WebGlyph() },
                    title = pattern,
                    subtitle = "Refused in the browser",
                    checked = pendingAt == null,
                    pending = pendingAt?.let { "Removal ${countdown(it)}" },
                    onChange = { on -> if (on) store.blockWeb(pattern) else store.unblockWeb(pattern); refresh() },
                )
            }
        }
    }
}
