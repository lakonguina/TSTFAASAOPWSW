package com.tstfaasaopwsw

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The browser's single-row glass bar, built entirely from [GlassKit] so it matches the rest
 * of the app: Back, the address field (grows), Tabs, a "⋮" menu (Forward / Reload / History),
 * and Settings. Even 44dp tap targets on one rhythm. Reads the page's backdrop from
 * [LocalBackdrop]; auto-hides on scroll (see [Browser]).
 */
@Composable
internal fun BrowserBottomBar(
    tab: Tab,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onGo: (String) -> Unit,
    onTabs: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    var text by remember(tab, tab.url) { mutableStateOf(tab.url) }
    var menuOpen by remember { mutableStateOf(false) }

    GlassBar {
        GlassIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, "Back", enabled = tab.canGoBack)

        Box(Modifier.weight(1f).padding(horizontal = Space.sm)) {
            if (text.isEmpty()) {
                Text(
                    "Search or enter address",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo(text) }),
            )
        }

        GlassSlotButton(onTabs) { tint -> TabsBadge(tabCount, tint) }
        GlassIconButton({ menuOpen = true }, Icons.Filled.MoreVert, "More")
        GlassIconButton(onSettings, Icons.Filled.Settings, "App settings")
    }

    if (menuOpen) {
        GlassBottomSheet(onDismiss = { menuOpen = false }) {
            GlassActionRow(Icons.AutoMirrored.Filled.ArrowForward, "Forward", enabled = tab.canGoForward) {
                menuOpen = false; onForward()
            }
            GlassActionRow(Icons.Filled.Refresh, "Reload") { menuOpen = false; onReload() }
            GlassActionRow(Icons.AutoMirrored.Filled.List, "History") { menuOpen = false; onHistory() }
        }
    }
}

/** The tab count in a rounded square, Safari-style, in the bar's current tint for contrast. */
@Composable
private fun TabsBadge(count: Int, tint: Color) {
    Box(
        Modifier.size(22.dp).border(2.dp, tint, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.coerceAtMost(99).toString(), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
