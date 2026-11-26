package com.tstfaasaopwsw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One facet row: title, a small line under it summarising the block state plus the daily
 * schedule (allowed hours + minutes/day), a block [Switch] on the right and a clock button
 * that opens a sheet to set the window and the daily limit together. The quota is stored
 * and shown; its enforcement (usage measurement) is not wired yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleRow(
    key: String,
    title: String,
    checked: Boolean,
    pendingAt: Long?,
    pendingVerb: String?,
    onChange: (Boolean) -> Unit,
    snap: Snapshot,
    refresh: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val window = remember(snap) { store.windowFor(key) }
    val quota = remember(snap) { store.quotaFor(key) }
    var sheetOpen by remember { mutableStateOf(false) }

    // Live refresh of the "time left" readout while a quota is set.
    var nowTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(quota, key) { if (quota != null) while (true) { delay(15_000); nowTick++ } }
    val remainingSec = remember(snap, quota, nowTick) {
        quota?.let {
            if (key.startsWith("app:")) {
                (it * 60L - AppUsage.foregroundSecondsToday(context, key.removePrefix("app:"))).coerceAtLeast(0L).toInt()
            } else store.quotaRemainingSeconds(key) ?: 0
        }
    }

    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (pendingAt != null) "$pendingVerb ${countdownLive(pendingAt)}" else summary(window, quota, remainingSec),
                style = MaterialTheme.typography.labelSmall,
                color = if (pendingAt != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete rule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { sheetOpen = true }) { Icon(Icons.Filled.DateRange, "Limit access") }
                Switch(checked = checked, onCheckedChange = onChange)
            }
        },
    )

    if (sheetOpen) {
        GlassBottomSheet(onDismiss = { sheetOpen = false }, title = "Limit access — $title") {
            ScheduleSheet(window, quota) { w, q ->
                store.setWindow(key, w); store.setQuota(key, q)
                PolicyManager.reconcile(context); sheetOpen = false; refresh()
            }
        }
    }
}

/**
 * "WEB — <domain>" section header with a "+" button right next to the title to add a rule
 * (block a specific URL on [domain]); the new rule is filed under this same service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebSectionHeader(domain: String, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var sheetOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(16.dp, 16.dp, 8.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "WEB — $domain",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { sheetOpen = true }) { Icon(Icons.Filled.Add, "Add a rule") }
    }

    if (sheetOpen) {
        var input by remember { mutableStateOf("https://$domain/") }
        GlassBottomSheet(onDismiss = { sheetOpen = false }, title = "Block a URL") {
            OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(),
                label = { Text("URL or path (e.g. https://x.com/settings)") }, singleLine = true, shape = GlassShape.field)
            Spacer(Modifier.height(Space.lg))
            Button(
                onClick = {
                    val p = input.trim()
                    if (p.isNotEmpty()) { store.addUserRule(p); PolicyManager.reconcile(context) }
                    sheetOpen = false; refresh()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = GlassShape.pill,
            ) { Text("Block") }
        }
    }
}

private fun summary(window: Window?, quota: Int?, remainingSec: Int?): String {
    val hours = if (window == null) "Always" else "${hhmm(window.startMin)}–${hhmm(window.endMin)}"
    val limit = quota?.let {
        val left = when {
            remainingSec == null -> ""
            remainingSec <= 0 -> " · used up"
            remainingSec < 60 -> " · <1 min left"
            else -> " · ${(remainingSec + 59) / 60} min left"
        }
        "$it min/day$left"
    } ?: "no daily limit"
    return "$hours · $limit"
}

@Composable
internal fun ScheduleSheet(window: Window?, quota: Int?, onApply: (Window?, Int?) -> Unit) {
    var startH by remember { mutableIntStateOf((window?.startMin ?: 480) / 60) }
    var endH by remember { mutableIntStateOf((window?.endMin ?: 1320) / 60) }
    var minutes by remember { mutableStateOf(quota?.toString() ?: "") }

    Column(Modifier.fillMaxWidth()) {
        Text("Allowed hours", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HourStepper("From", startH) { startH = it.coerceIn(0, 23) }
        HourStepper("To", endH) { endH = it.coerceIn(1, 24) }
        Spacer(Modifier.height(Space.md))
        OutlinedTextField(
            value = minutes,
            onValueChange = { s -> minutes = s.filter { it.isDigit() }.take(4) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Daily limit (minutes, blank = none)") },
            singleLine = true,
            shape = GlassShape.field,
        )
        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TextButton(onClick = { onApply(null, null) }, modifier = Modifier.weight(1f)) { Text("No limit") }
            Button(
                onClick = { onApply(Window(startH * 60, endH * 60), minutes.toIntOrNull()) },
                modifier = Modifier.weight(1f),
                shape = GlassShape.pill,
            ) { Text("Save") }
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(hour - 1) }) { Icon(Icons.Filled.KeyboardArrowDown, "minus") }
            Text(hhmm(hour * 60), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
            IconButton(onClick = { onChange(hour + 1) }) { Icon(Icons.Filled.KeyboardArrowUp, "plus") }
        }
    }
}

internal fun hhmm(min: Int): String = "%02d:%02d".format((min / 60) % 24, min % 60)
