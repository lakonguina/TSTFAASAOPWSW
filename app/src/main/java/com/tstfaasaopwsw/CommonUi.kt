package com.tstfaasaopwsw

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/** Generic row: icon + title + subtitle (+ countdown) + switch. */
@Composable
internal fun ToggleRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    pending: String?,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = leading,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (pending != null) Text(
                    "$pending (still locked until then)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/** "in 2 h 5 min" / "in 9 min" / "in less than 1 min". */
internal fun countdown(at: Long): String {
    val totalMin = ((at - System.currentTimeMillis()) / 60_000L)
    return when {
        totalMin <= 0L -> "in less than 1 min"
        totalMin < 60L -> "in $totalMin min"
        totalMin % 60L == 0L -> "in ${totalMin / 60} h"
        else -> "in ${totalMin / 60} h ${totalMin % 60} min"
    }
}
