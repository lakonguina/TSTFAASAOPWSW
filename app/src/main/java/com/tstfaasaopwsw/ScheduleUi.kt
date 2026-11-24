package com.tstfaasaopwsw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * "Allowed hours" control for one facet (its [key]). Shows the current daily window and
 * opens a dialog to set or clear it. Outside the window the facet is blocked; the change
 * is applied through [PolicyManager.reconcile] so apps re-hide and the boundary alarm is
 * rescheduled at once.
 */
@Composable
internal fun AllowedHoursRow(key: String, snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val window = remember(snap) { store.windowFor(key) }
    var editing by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable { editing = true },
        leadingContent = { Icon(Icons.Filled.DateRange, null) },
        headlineContent = { Text("Allowed hours") },
        supportingContent = {
            Text(if (window == null) "Always" else "${hhmm(window.startMin)} – ${hhmm(window.endMin)}, blocked otherwise")
        },
    )

    if (editing) {
        val apply: (Window?) -> Unit = { w ->
            store.setWindow(key, w); PolicyManager.reconcile(context); editing = false; refresh()
        }
        WindowDialog(window, onDismiss = { editing = false }, onApply = apply)
    }
}

@Composable
private fun WindowDialog(current: Window?, onDismiss: () -> Unit, onApply: (Window?) -> Unit) {
    var startH by remember { mutableIntStateOf((current?.startMin ?: 480) / 60) }
    var endH by remember { mutableIntStateOf((current?.endMin ?: 1320) / 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allowed hours") },
        text = {
            Column {
                HourStepper("From", startH) { startH = it.coerceIn(0, 23) }
                HourStepper("To", endH) { endH = it.coerceIn(1, 24) }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(Window(startH * 60, endH * 60)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onApply(null) }) { Text("No limit") } },
    )
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(hour - 1) }) { Icon(Icons.Filled.KeyboardArrowDown, "minus") }
            Text(hhmm(hour * 60), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onChange(hour + 1) }) { Icon(Icons.Filled.KeyboardArrowUp, "plus") }
        }
    }
}

private fun hhmm(min: Int): String = "%02d:%02d".format((min / 60) % 24, min % 60)
