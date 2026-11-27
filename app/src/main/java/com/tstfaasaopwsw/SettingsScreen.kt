package com.tstfaasaopwsw

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** General tab: a status hero, then an iOS-style grouped "Reflection delay" section. */
@Composable
internal fun SettingsScreen(snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val insets = GlassContentInsets

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        item { GlassLargeTitle("General") }
        item { StatusCard(snap) }
        item { SectionHeader("Reflection delay") }
        item {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Current", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            Config.formatDelay(snap.delayMinutes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    snap.pendingDelay?.let { (v, at) ->
                        Text(
                            "→ changes to ${Config.formatDelay(v)} ${countdownLive(at)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DelayPicker(snap.delayMinutes) { store.requestDelay(it); refresh() }
                }
            }
        }
        item {
            Text(
                "Raising the delay (or blocking an app) takes effect immediately. Lowering it — " +
                    "or unblocking — only takes effect after the current delay: until then, it stays locked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Space.xl, Space.md, Space.xl, Space.lg),
            )
        }
        if (!AppUsage.hasPermission(context)) {
            item { SectionHeader("App time limits") }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(
                            "A daily minutes limit on an app needs the \"Usage access\" permission to " +
                                "measure its screen time. Web and feed limits work without it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text("Grant usage access") }
                    }
                }
            }
        }
        item { SectionHeader("Firewall") }
        item {
            var lockdown by remember { mutableStateOf(store.firewallLockdown) }
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Lockdown mode", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = lockdown,
                            onCheckedChange = { lockdown = it; store.setFirewallLockdown(it); refresh() },
                        )
                    }
                    Text(
                        "The domain filter runs through an always-on VPN. Lockdown blocks all traffic " +
                            "if that VPN ever fails — maximum enforcement, but a filter bug cuts the network " +
                            "until you fix it over adb. Leave off until the filter is proven on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(Space.lg)) }
    }
}
