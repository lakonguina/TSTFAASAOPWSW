package com.khazaddum.forcelist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SectionHeader(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
)

@Composable
internal fun StatusCard(snap: Snapshot) {
    val ok = snap.isDeviceOwner
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(if (ok) Icons.Filled.Lock else Icons.Filled.Settings, null, Modifier.padding(end = 12.dp))
            Column {
                Text(
                    if (ok) "Appareil sous contrôle" else "Contrôle inactif",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (ok) "${snap.blocked.size} app(s) bloquée(s) · ${snap.extensions.size} extension(s) forcée(s)"
                    else snap.statusText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun OwnerWarning(status: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
    }
}
