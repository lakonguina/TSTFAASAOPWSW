package com.tstfaasaopwsw

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

/** General tab: status + global filter + reflection delay (the picker lives in [DelayPicker]). */
@Composable
internal fun SettingsScreen(snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { StatusCard(snap) }
        item { SectionHeader("Global filter") }
        item {
            ToggleRow(
                leading = { WebGlyph() },
                title = "Filter the whole phone",
                subtitle = if (snap.firewallEnabled) "On — blocked sites are blocked in every app"
                else "Off — only this app's browser filters",
                checked = snap.firewallEnabled,
                pending = null,
                onChange = { on -> FirewallPolicy.setFirewall(context, on); refresh() },
            )
        }
        item { SectionHeader("Reflection delay") }
        item {
            Text(
                "Current: ${Config.formatDelay(snap.delayMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp, 0.dp),
            )
        }
        snap.pendingDelay?.let { (v, at) ->
            item {
                Box(Modifier.padding(16.dp, 2.dp)) {
                    Text(
                        "→ changes to ${Config.formatDelay(v)} ${countdown(at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { DelayPicker(snap.delayMinutes) { store.requestDelay(it); refresh() } }
        item {
            Text(
                "Raising the delay (or blocking an app) takes effect immediately. Lowering it " +
                    "— or unblocking — only takes effect after the current delay: until then, " +
                    "it stays locked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 16.dp),
            )
        }
    }
}
