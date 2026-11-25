package com.tstfaasaopwsw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun SectionHeader(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(Space.xl, Space.lg, Space.xl, Space.sm),
)

@Composable
internal fun StatusCard(snap: Snapshot) {
    val ok = snap.isDeviceOwner
    val accent = if (ok) MaterialTheme.colorScheme.primary else Color(0xFFFF3B30)
    GlassCard(Modifier.fillMaxWidth().padding(Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(if (ok) Icons.Filled.Lock else Icons.Filled.Settings, accent)
            Column(Modifier.padding(start = Space.md)) {
                Text(
                    if (ok) "Device under control" else "Control inactive",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (ok) "${snap.blocked.size} blocked app(s) · ${snap.blockedWeb.size} web rule(s)"
                    else snap.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun OwnerWarning(status: String) {
    GlassCard(Modifier.fillMaxWidth().padding(Space.lg, Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(Icons.Filled.Settings, Color(0xFFFF3B30))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Space.md),
            )
        }
    }
}

/** A small rounded tinted badge holding an icon — the app's one status glyph treatment. */
@Composable
private fun StatusBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color) {
    Box(
        Modifier.size(38.dp)
            .background(accent.copy(alpha = 0.16f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
    }
}
