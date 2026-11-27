package com.tstfaasaopwsw

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Lists ("playlists") overview: every list with its rule count, plus create. */
@Composable
internal fun ListsScreen(snap: Snapshot, refresh: () -> Unit, onOpen: (RuleList) -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val lists = remember(snap) { store.ruleLists() }
    var createOpen by remember { mutableStateOf(false) }

    val insets = GlassContentInsets
    Column(Modifier.fillMaxSize()) {
        GlassLargeTitle("Lists", Modifier.padding(top = insets.calculateTopPadding())) {
            FilledTonalIconButton(onClick = { createOpen = true }) { Icon(Icons.Filled.Add, "New list") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = insets.calculateBottomPadding())) {
            items(lists, key = { it.id }) { list ->
                val label = remember(snap, list.id) {
                    if (list.imported) "${store.importCount(list.id)} domain(s) · imported"
                    else "${store.membersOf(list.id).size} rule(s)"
                }
                ListItem(
                    modifier = Modifier.clickable { onOpen(list) },
                    leadingContent = { Icon(Icons.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(list.name) },
                    supportingContent = { Text(label) },
                    trailingContent = { Icon(Icons.Filled.KeyboardArrowRight, null) },
                )
            }
        }
    }

    if (createOpen) {
        NewListSheet(onDismiss = { createOpen = false }, refresh = refresh)
    }
}

/** Create an empty list, or import a domain blocklist from a URL (preset or pasted). */
@Composable
private fun NewListSheet(onDismiss: () -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val scope = rememberCoroutineScope()
    var importMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassBottomSheet(onDismiss = onDismiss, title = if (importMode) "Import a blocklist" else "New list") {
        Column {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text("List name") }, singleLine = true, shape = GlassShape.field, enabled = !busy,
            )
            if (!importMode) {
                Spacer(Modifier.height(Space.lg))
                Button(
                    onClick = { if (name.isNotBlank()) { store.createList(name); onDismiss(); refresh() } },
                    modifier = Modifier.fillMaxWidth(), shape = GlassShape.pill,
                ) { Text("Create") }
                Spacer(Modifier.height(Space.xs))
                TextButton({ importMode = true }, Modifier.fillMaxWidth()) { Text("Import a blocklist instead") }
            } else {
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(),
                    label = { Text("Blocklist URL (hosts file)") }, singleLine = true, shape = GlassShape.field, enabled = !busy,
                )
                Spacer(Modifier.height(Space.xs))
                FlowRowPresets(enabled = !busy) { label, presetUrl ->
                    if (name.isBlank()) name = label; url = presetUrl
                }
                error?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF3B30))
                }
                Spacer(Modifier.height(Space.lg))
                Button(
                    enabled = !busy && url.isNotBlank(),
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { Downloads.getText(url.trim()) }
                            val domains = text?.let { parseBlocklist(it) } ?: emptySet()
                            if (domains.isEmpty()) {
                                error = "Couldn't fetch or parse that list."; busy = false
                            } else {
                                val id = store.createList(name.ifBlank { "Imported list" }, url.trim())
                                store.setImportDomains(id, domains)
                                PolicyManager.reconcile(context)
                                onDismiss(); refresh()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), shape = GlassShape.pill,
                ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Import") }
            }
        }
    }
}

@Composable
private fun FlowRowPresets(enabled: Boolean, onPick: (String, String) -> Unit) {
    Column {
        Config.blocklistPresets.forEach { (label, presetUrl) ->
            TextButton({ onPick(label, presetUrl) }, Modifier.fillMaxWidth(), enabled = enabled) {
                Text("Use preset: $label", Modifier.fillMaxWidth())
            }
        }
    }
}

/** One list's rules: each a [RuleRow], plus add-rule, rename and delete. */
@Composable
internal fun ListDetailScreen(
    list: RuleList,
    snap: Snapshot,
    refresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    if (list.imported) { ImportedListDetail(list, onBack); return }
    val facets = remember(snap, list.id) { store.membersOf(list.id) }
    val lWindow = remember(snap, list.id) { store.windowFor(listKey(list.id)) }
    val lQuota = remember(snap, list.id) { store.quotaFor(listKey(list.id)) }
    val lLeft = remember(snap, list.id) { store.listQuotaRemainingSeconds(list.id) }
    var addOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }

    val insets = GlassContentInsets
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(insets.calculateTopPadding()))
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(Space.md, Space.xs, Space.lg, Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            GlassIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, "Back")
            Text(
                list.name, style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            GlassIconButton({ scheduleOpen = true }, Icons.Filled.DateRange, "List schedule")
            if (!list.isDefault) GlassIconButton({ renameOpen = true }, Icons.Filled.Edit, "Rename")
            if (!list.isDefault) GlassIconButton(
                { store.deleteList(list.id); onBack() }, Icons.Filled.Delete, "Delete list", tint = Color(0xFFFF3B30),
            )
            FilledTonalIconButton(onClick = { addOpen = true }) { Icon(Icons.Filled.Add, "Add rule") }
        }
        Text(
            listScheduleSummary(lWindow, lQuota, lLeft),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Space.xl, 0.dp, Space.xl, Space.sm),
        )
        if (facets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Space.xxl), Alignment.TopCenter) {
                Text("No rules yet. Tap + to add an app or a site.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = insets.calculateBottomPadding())) {
                items(facets, key = { it }) { facet -> MemberRow(facet, snap, refresh) }
            }
        }
    }

    if (addOpen) {
        AddToListSheet(listId = list.id, onDismiss = { addOpen = false }, refresh = refresh)
    }
    if (scheduleOpen) {
        GlassBottomSheet(onDismiss = { scheduleOpen = false }, title = "List schedule — ${list.name}") {
            ScheduleSheet(lWindow, lQuota) { w, q ->
                store.setWindow(listKey(list.id), w); store.setQuota(listKey(list.id), q)
                PolicyManager.reconcile(context); scheduleOpen = false; refresh()
            }
        }
    }
    if (renameOpen) {
        var name by remember { mutableStateOf(list.name) }
        GlassBottomSheet(onDismiss = { renameOpen = false }, title = "Rename list") {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text("List name") }, singleLine = true, shape = GlassShape.field)
            Spacer(Modifier.height(Space.lg))
            Button(
                onClick = { store.renameList(list.id, name); renameOpen = false; refresh() },
                modifier = Modifier.fillMaxWidth(), shape = GlassShape.pill,
            ) { Text("Save") }
        }
    }
}

/** An imported blocklist: its domain count, source, and removal. No per-rule rows (too many). */
@Composable
private fun ImportedListDetail(list: RuleList, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val count = remember(list.id) { store.importCount(list.id) }
    var pendingAt by remember { mutableStateOf(store.pendingImportRemovalAt(list.id)) }
    val insets = GlassContentInsets
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(insets.calculateTopPadding()))
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(Space.md, Space.xs, Space.lg, Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            GlassIconButton(onBack, Icons.AutoMirrored.Filled.ArrowBack, "Back")
            Text(
                list.name, style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        GlassCard(Modifier.fillMaxWidth().padding(Space.lg)) {
            Column {
                Text("Imported blocklist", style = MaterialTheme.typography.titleSmall)
                Text("$count domain(s) blocked everywhere", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.sourceUrl?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(Space.lg))
                val at = pendingAt
                if (at != null) {
                    Text(
                        "Removal pending — unlocks ${countdownLive(at)}. The list stays active until then.",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = {
                            store.removeImportedList(list.id); PolicyManager.reconcile(context)
                            val now = store.pendingImportRemovalAt(list.id)
                            if (now == null) onBack() else pendingAt = now // deferred by the reflection delay
                        },
                        modifier = Modifier.fillMaxWidth(), shape = GlassShape.pill,
                    ) { Text("Remove this blocklist") }
                }
            }
        }
    }
}

/** One rule inside a list, resolved from its facet key to a [RuleRow]. */
@Composable
private fun MemberRow(facet: String, snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    when {
        facet.startsWith("app:") -> {
            val pkg = facet.removePrefix("app:")
            val at = snap.pendingUnblock[pkg]
            RuleRow(
                key = facet, title = appLabel(context, pkg),
                checked = pkg in snap.blocked && at == null,
                pendingAt = at, pendingVerb = "Unblock",
                onChange = { on -> if (on) store.block(pkg) else store.unblock(pkg); PolicyManager.reconcile(context); refresh() },
                snap = snap, refresh = refresh,
                onDelete = { store.unblock(pkg); store.unassign(facet); PolicyManager.reconcile(context); refresh() },
            )
        }
        facet.startsWith("web:") -> {
            val pattern = facet.removePrefix("web:")
            val at = snap.pendingWebRemoval[pattern]
            RuleRow(
                key = facet, title = pattern,
                checked = pattern in snap.blockedWeb && at == null,
                pendingAt = at, pendingVerb = "Unblock",
                onChange = { on -> if (on) store.blockWeb(pattern) else store.unblockWeb(pattern); PolicyManager.reconcile(context); refresh() },
                snap = snap, refresh = refresh,
                onDelete = {
                    if (pattern in store.userRules()) store.deleteUserRule(pattern) else store.unblockWeb(pattern)
                    store.unassign(facet); PolicyManager.reconcile(context); refresh()
                },
            )
        }
        facet.startsWith("elem:") -> {
            val id = facet.removePrefix("elem:")
            val at = snap.pendingElement[id]
            RuleRow(
                key = facet, title = "${id.substringBefore("|")} — ${id.substringAfter("|")}",
                checked = id in snap.enabledElements && at == null,
                pendingAt = at, pendingVerb = "Show again",
                onChange = { on -> if (on) store.enableElement(id) else store.disableElement(id); ElementHider.sync(store); PolicyManager.reconcile(context); refresh() },
                snap = snap, refresh = refresh,
                onDelete = { store.disableElement(id); store.unassign(facet); ElementHider.sync(store); PolicyManager.reconcile(context); refresh() },
            )
        }
    }
}

/**
 * Add-to-list flow: search apps + sites, then pick what exactly to add (whole app, whole
 * site, a feed/sidebar to hide, or a specific URL) — so a list keeps every web granularity
 * the old service screen had. Each choice blocks that facet and files it under [listId].
 */
@Composable
private fun AddToListSheet(listId: String, onDismiss: () -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<ServiceInfo?>(null) }
    var query by remember { mutableStateOf("") }
    val services by produceState<List<ServiceInfo>?>(null) { value = loadServices(context) }

    val chosen = picked
    if (chosen == null) {
        val q = query.trim()
        val filtered = services?.filter { q.isEmpty() || it.label.contains(q, true) || it.domain?.contains(q, true) == true }
        GlassBottomSheet(onDismiss = onDismiss, title = "Add an app or site") {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search") }, singleLine = true, shape = GlassShape.field,
            )
            Spacer(Modifier.height(Space.sm))
            if (filtered == null) {
                Box(Modifier.fillMaxWidth().padding(Space.xl), Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(filtered.take(60), key = { "pick:${it.key}" }) { s ->
                        ListItem(
                            modifier = Modifier.clickable { picked = s },
                            leadingContent = { ServiceIcon(s, 36.dp) },
                            headlineContent = { Text(s.label) },
                            supportingContent = s.domain?.let { { Text(it) } },
                        )
                    }
                }
            }
        }
    } else {
        SiteOptionsSheet(chosen, listId, onDismiss, refresh)
    }
}

/** What exactly to block for the chosen app/site, each filed under the list. */
@Composable
private fun SiteOptionsSheet(service: ServiceInfo, listId: String, onDismiss: () -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    fun done() { PolicyManager.reconcile(context); onDismiss(); refresh() }

    GlassBottomSheet(onDismiss = onDismiss, title = service.label) {
        Column {
            if (service.packages.isNotEmpty()) {
                GlassActionRow(Icons.Filled.Lock, "Block the app") {
                    service.packages.forEach { store.block(it); store.assignToList(ScheduleKeys.app(it), listId) }
                    done()
                }
            }
            val domain = service.domain
            if (domain != null) {
                GlassActionRow(Icons.Filled.Public, "Block the whole site") {
                    store.blockWeb(domain); store.assignToList(ScheduleKeys.web(domain), listId); done()
                }
                Config.elementsFor(domain).forEach { rule ->
                    GlassActionRow(Icons.Filled.VisibilityOff, "Hide ${rule.name}") {
                        store.enableElement(rule.id); store.assignToList(ScheduleKeys.elem(rule.id), listId)
                        ElementHider.sync(store); done()
                    }
                }
                var url by remember { mutableStateOf("https://$domain/") }
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(),
                    label = { Text("Block a URL (e.g. $domain/settings)") }, singleLine = true, shape = GlassShape.field,
                )
                Spacer(Modifier.height(Space.sm))
                Button(
                    onClick = { val p = url.trim(); if (p.isNotEmpty()) { store.addUserRule(p); store.assignToList(ScheduleKeys.web(p), listId) }; done() },
                    modifier = Modifier.fillMaxWidth(), shape = GlassShape.pill,
                ) { Text("Block this URL") }
            }
        }
    }
}

private fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)).toString()
}.getOrDefault(pkg)

private fun listScheduleSummary(window: Window?, quota: Int?, remainingSec: Int?): String {
    val hours = if (window == null) "any hours" else "${hhmm(window.startMin)}–${hhmm(window.endMin)}"
    val q = quota?.let {
        val left = when {
            remainingSec == null -> ""
            remainingSec <= 0 -> " · used up"
            remainingSec < 60 -> " · <1 min left"
            else -> " · ${(remainingSec + 59) / 60} min left"
        }
        "$it min/day shared$left"
    } ?: "no shared limit"
    return "List limit: $hours · $q"
}
