package com.tstfaasaopwsw

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The browser's single-row glass bar plus a Safari-style address field: Back, the address
 * field (grows), Tabs, a "⋮" menu (Forward / Reload / History), and Settings. Tapping the
 * field opens an edit mode — the text is selected, a live suggestions panel appears above the
 * bar (matching history, and a search/open-URL row), and a clear "✕" replaces the reload icon.
 * The bar rides above the keyboard (see [GlassBar]); it auto-hides on scroll (see [Browser]).
 */
@Composable
internal fun BrowserBottomBar(
    tab: Tab,
    tabCount: Int,
    history: List<String>,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onGo: (String) -> Unit,
    onTabs: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onDownloads: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onFind: () -> Unit,
    onToggleDesktop: () -> Unit,
    onSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var field by remember(tab) { mutableStateOf(TextFieldValue(tab.url)) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Keep the field showing the live URL whenever we are not actively editing it.
    LaunchedEffect(tab.url) { if (!editing) field = TextFieldValue(tab.url) }

    // On entering edit mode, select the whole URL (Safari-style) so the next keystroke
    // replaces it. Done in an effect so it runs AFTER the tap sets its own cursor position,
    // which would otherwise cancel the selection.
    LaunchedEffect(editing) {
        if (editing) field = field.copy(selection = TextRange(0, field.text.length))
    }

    fun stopEditing() { editing = false; keyboard?.hide() }
    fun submit(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) onGo(t)
        stopEditing()
    }

    val query = field.text
    val suggestions = remember(query, editing, history) {
        if (!editing || query.isBlank()) emptyList()
        else history.filter { it.contains(query, ignoreCase = true) && it != query }
            .distinct().take(6)
    }

    Column(Modifier.fillMaxWidth()) {
        if (editing) {
            SuggestionsPanel(
                query = query,
                suggestions = suggestions,
                onPick = { submit(it) },
            )
        }

        GlassBar {
            GlassIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, "Back", enabled = tab.canGoBack)

            // The address field: a frosted rounded pill, like Safari's URL bar.
            Row(
                Modifier.weight(1f)
                    .frosted(GlassShape.field)
                    .padding(horizontal = Space.sm, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Icon(
                    Icons.Filled.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Box(Modifier.weight(1f)) {
                    if (field.text.isEmpty()) {
                        Text(
                            "Search or enter address",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { s ->
                                if (s.isFocused) editing = true else editing = false
                            },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit(field.text) }),
                    )
                }
                // Clear while editing, reload otherwise.
                if (editing && field.text.isNotEmpty()) {
                    FieldIcon(Icons.Filled.Clear, "Clear") { field = TextFieldValue("") }
                } else if (!editing) {
                    FieldIcon(Icons.Filled.Refresh, "Reload", onReload)
                }
            }

            GlassSlotButton(onTabs) { tint -> TabsBadge(tabCount, tint) }
            GlassIconButton({ menuOpen = true }, Icons.Filled.MoreVert, "More")
            GlassIconButton(onSettings, Icons.Filled.Settings, "App settings")
        }
    }

    if (menuOpen) {
        GlassBottomSheet(onDismiss = { menuOpen = false }) {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                GlassActionRow(Icons.AutoMirrored.Filled.ArrowForward, "Forward", enabled = tab.canGoForward) {
                    menuOpen = false; onForward()
                }
                GlassActionRow(Icons.Filled.Refresh, "Reload") { menuOpen = false; onReload() }
                GlassActionRow(
                    if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    if (isBookmarked) "Remove bookmark" else "Add bookmark",
                ) { menuOpen = false; onToggleBookmark() }
                GlassActionRow(Icons.Filled.Bookmarks, "Bookmarks") { menuOpen = false; onBookmarks() }
                GlassActionRow(Icons.Filled.History, "History") { menuOpen = false; onHistory() }
                GlassActionRow(Icons.Filled.Download, "Downloads") { menuOpen = false; onDownloads() }
                GlassActionRow(Icons.Filled.Share, "Share") { menuOpen = false; onShare() }
                GlassActionRow(Icons.Filled.ContentCopy, "Copy address") { menuOpen = false; onCopyLink() }
                GlassActionRow(Icons.Filled.Search, "Find in page") { menuOpen = false; onFind() }
                GlassActionRow(
                    if (tab.desktop) Icons.Filled.PhoneAndroid else Icons.Filled.DesktopMac,
                    if (tab.desktop) "Mobile site" else "Desktop site",
                ) { menuOpen = false; onToggleDesktop() }
            }
        }
    }
}

/**
 * Find-in-page bar: a text field that searches as you type, previous/next steppers, and a
 * close button. Replaces the address bar while active; rides above the keyboard like it.
 */
@Composable
internal fun FindBar(
    onFind: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    GlassBar {
        Icon(
            Icons.Filled.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).padding(start = Space.xs),
        )
        Box(Modifier.weight(1f).padding(horizontal = Space.sm)) {
            if (text.isEmpty()) Text(
                "Find in page",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = text,
                onValueChange = { text = it; onFind(it, true) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onFind(text, true) }),
            )
        }
        GlassIconButton({ onFind(text, false) }, Icons.Filled.KeyboardArrowUp, "Previous")
        GlassIconButton({ onFind(text, true) }, Icons.Filled.KeyboardArrowDown, "Next")
        GlassIconButton(onClose, Icons.Filled.Clear, "Close find")
    }
}

/** The long-press context menu for a link or image, as a bottom sheet. */
@Composable
internal fun ContextMenuSheet(
    target: ContextTarget,
    onDismiss: () -> Unit,
    onOpenNewTab: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDownload: (String) -> Unit,
) {
    val raw = target.link ?: target.image
    GlassBottomSheet(onDismiss = onDismiss, title = raw?.let { UrlRules.host(it).ifEmpty { it } }) {
        Column {
            target.link?.let { link ->
                GlassActionRow(Icons.Filled.OpenInNew, "Open in new tab") { onDismiss(); onOpenNewTab(link) }
                GlassActionRow(Icons.Filled.ContentCopy, "Copy link") { onDismiss(); onCopy(link) }
                GlassActionRow(Icons.Filled.Share, "Share link") { onDismiss(); onShare(link) }
            }
            target.image?.let { img ->
                GlassActionRow(Icons.Filled.OpenInNew, "Open image in new tab") { onDismiss(); onOpenNewTab(img) }
                GlassActionRow(Icons.Filled.Download, "Download image") { onDismiss(); onDownload(img) }
                GlassActionRow(Icons.Filled.ContentCopy, "Copy image address") { onDismiss(); onCopy(img) }
            }
        }
    }
}

/** Suggestions above the bar: a "search / open" row for the typed text, then history matches. */
@Composable
private fun SuggestionsPanel(
    query: String,
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    if (query.isBlank()) return
    val urlLike = query.contains("://") || (query.contains('.') && !query.contains(' '))
    GlassCard(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs),
        padding = PaddingValues(Space.xs),
    ) {
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            SuggestionRow(
                icon = if (urlLike) Icons.Filled.Lock else Icons.Filled.Search,
                primary = if (urlLike) query else "Search “$query”",
                onClick = { onPick(query) },
            )
            suggestions.forEach { url ->
                SuggestionRow(icon = Icons.Filled.Lock, primary = UrlRules.host(url), secondary = url) { onPick(url) }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    primary: String,
    secondary: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .pressable(GlassShape.field, onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                primary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) Text(
                secondary, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small tap target inside the address field (clear / reload). */
@Composable
private fun FieldIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).pressable(GlassShape.pill, bounded = false, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
