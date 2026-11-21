package com.khazaddum.forcelist

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/** Ligne générique : icône + titre + sous-titre (+ compte à rebours) + interrupteur. */
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
                    "$pending (toujours verrouillé en attendant)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/** "dans 2 h 5 min" / "dans 9 min" / "dans moins d'1 min". */
internal fun countdown(at: Long): String {
    val totalMin = ((at - System.currentTimeMillis()) / 60_000L)
    return when {
        totalMin <= 0L -> "dans moins d'1 min"
        totalMin < 60L -> "dans $totalMin min"
        totalMin % 60L == 0L -> "dans ${totalMin / 60} h"
        else -> "dans ${totalMin / 60} h ${totalMin % 60} min"
    }
}
