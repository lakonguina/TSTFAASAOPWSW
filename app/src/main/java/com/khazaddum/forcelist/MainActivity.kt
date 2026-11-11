package com.khazaddum.forcelist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deux onglets en Jetpack Compose Material 3 :
 *   - Blocages : une seule liste = extensions forçables (Edge) + toutes les apps,
 *     chacune avec un interrupteur.
 *   - Réglages : délai de réflexion + statut.
 *
 * L'interrupteur reflète l'INTENTION (verrouillé / libre). Serrer prend effet tout
 * de suite ; desserrer est différé du délai : l'interrupteur bascule quand même, et
 * un compte à rebours indique quand ce sera effectif (façon Pluckeye / Andoff).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ForcelistTheme { ForcelistApp() } }
    }
}

// ---------------------------------------------------------------------------
// Thème : Material You (couleurs dynamiques Android 12+), sinon palette par défaut.
// ---------------------------------------------------------------------------

@Composable
internal fun ForcelistTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

// ---------------------------------------------------------------------------
// Modèle d'affichage.
// ---------------------------------------------------------------------------

private data class Snapshot(
    val statusText: String,
    val isDeviceOwner: Boolean,
    val blocked: Set<String>,
    val extensions: Set<String>,
    val blockedWeb: Set<String>,
    val firewallEnabled: Boolean,
    val delayMinutes: Long,
    val pendingDelay: Pair<Long, Long>?,
    val pendingUnblock: Map<String, Long>,
    val pendingExtRemoval: Map<String, Long>,
    val pendingWebRemoval: Map<String, Long>,
)

private data class AppInfo(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap,
    val isLaunchable: Boolean,
)

private fun buildSnapshot(context: Context): Snapshot {
    val report = PolicyManager.reconcile(context)
    val store = Store.get(context)
    return Snapshot(
        statusText = report.text,
        isDeviceOwner = report.isDeviceOwner,
        blocked = store.blocked,
        extensions = store.extensions,
        blockedWeb = store.blockedWeb,
        firewallEnabled = store.firewallEnabled,
        delayMinutes = store.delayMinutes,
        pendingDelay = store.pendingDelay(),
        pendingUnblock = store.pendingUnblocks(),
        pendingExtRemoval = store.pendingExtRemovals(),
        pendingWebRemoval = store.pendingWebRemovals(),
    )
}

/**
 * Les apps que l'utilisateur reconnaît : celles qui ont une icône dans le tiroir
 * (lançables), plus celles déjà gérées (pour pouvoir les débloquer même masquées).
 * Pas les centaines de services système.
 */
private suspend fun loadApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val self = context.packageName
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
    val managed = Store.get(context).known
    ((launchable + managed) - self)
        .mapNotNull { pkg ->
            runCatching {
                val ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                AppInfo(
                    pkg = pkg,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = pm.getApplicationIcon(ai).toSquareBitmap(),
                    isLaunchable = pkg in launchable,
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

private fun Drawable.toSquareBitmap(px: Int = 96): ImageBitmap =
    toBitmap(width = px, height = px).asImageBitmap()

/** "dans 2 h 5 min" / "dans 9 min" / "dans moins d'1 min". */
private fun countdown(at: Long): String {
    val totalMin = ((at - System.currentTimeMillis()) / 60_000L)
    return when {
        totalMin <= 0L -> "dans moins d'1 min"
        totalMin < 60L -> "dans $totalMin min"
        totalMin % 60L == 0L -> "dans ${totalMin / 60} h"
        else -> "dans ${totalMin / 60} h ${totalMin % 60} min"
    }
}

// ---------------------------------------------------------------------------
// Coquille : barre du haut + navigation du bas.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForcelistApp() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val snapshot = remember(tick) { buildSnapshot(context) }
    val refresh: () -> Unit = { tick++ }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val apps = produceState<List<AppInfo>?>(initialValue = null, context) { value = loadApps(context) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("ForceList") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Lock, null) }, label = { Text("Apps") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Web") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Réglages") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> BlocagesScreen(apps.value, snapshot, refresh)
                1 -> WebScreen(snapshot, refresh)
                else -> SettingsScreen(snapshot, refresh)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Onglet Blocages : une seule liste (extensions + toutes les apps).
// ---------------------------------------------------------------------------

@Composable
private fun BlocagesScreen(apps: List<AppInfo>?, snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var query by rememberSaveable { mutableStateOf("") }

    val q = query.trim()
    fun matches(vararg fields: String) = q.isEmpty() || fields.any { it.contains(q, true) }

    val catalogIds = remember { Config.extensionCatalog.map { it.id }.toSet() }
    val exts = Config.extensionCatalog.filter { matches(it.name, it.id) }
    val orphanExts = snap.extensions.filter { it.substringBefore(';') !in catalogIds && matches(it) }
    val filteredApps = apps?.filter { matches(it.label, it.pkg) }

    Column(Modifier.fillMaxSize()) {
        if (!snap.isDeviceOwner) OwnerWarning(snap.statusText)

        // Recherche unique, en tête : filtre aussi bien les extensions que les apps.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 8.dp),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Rechercher une app ou extension") },
            singleLine = true,
        )

        // Une seule liste : extensions (en haut) puis apps, sans séparation.
        LazyColumn(Modifier.fillMaxSize()) {
            items(exts, key = { "ext:${it.id}" }) { ext ->
                val pendingAt = snap.pendingExtRemoval[ext.value]
                ToggleRow(
                    leading = { ExtensionGlyph() },
                    title = ext.name,
                    subtitle = "Extension Edge",
                    checked = ext.value in snap.extensions && pendingAt == null,
                    pending = pendingAt?.let { "Retrait ${countdown(it)}" },
                    onChange = { on -> if (on) store.addExtension(ext.value) else store.removeExtension(ext.value); refresh() },
                )
            }
            items(orphanExts, key = { "extx:$it" }) { ext ->
                val pendingAt = snap.pendingExtRemoval[ext]
                ToggleRow(
                    leading = { ExtensionGlyph() },
                    title = ext.substringBefore(';'),
                    subtitle = "Extension Edge",
                    checked = pendingAt == null,
                    pending = pendingAt?.let { "Retrait ${countdown(it)}" },
                    onChange = { _ -> store.removeExtension(ext); refresh() },
                )
            }
            if (filteredApps == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            } else {
                items(filteredApps, key = { "app:${it.pkg}" }) { app ->
                    val pendingAt = snap.pendingUnblock[app.pkg]
                    ToggleRow(
                        leading = { Image(app.icon, null, Modifier.size(40.dp)) },
                        title = app.label,
                        subtitle = app.pkg,
                        checked = app.pkg in snap.blocked && pendingAt == null,
                        pending = pendingAt?.let { "Déblocage ${countdown(it)}" },
                        onChange = { on -> if (on) store.block(app.pkg) else store.unblock(app.pkg); refresh() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionGlyph() = Box(
    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
    Alignment.Center,
) {
    Icon(Icons.Filled.Build, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
}

/** Ligne générique icône + titre + sous-titre (+ compte à rebours) + interrupteur. */
@Composable
private fun ToggleRow(
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

// ---------------------------------------------------------------------------
// Onglet Web : ouvrir le navigateur + liste des sites/URL bloqués.
// ---------------------------------------------------------------------------

@Composable
private fun WebScreen(snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var input by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, BrowserActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
        ) { Text("Ouvrir le navigateur") }

        ToggleRow(
            leading = { WebGlyph() },
            title = "Filtrer tout le téléphone",
            subtitle = if (snap.firewallEnabled) "Actif — les sites bloqués le sont partout"
            else "Inactif — seul le navigateur filtre",
            checked = snap.firewallEnabled,
            pending = null,
            onChange = { on -> PolicyManager.setFirewall(context, on); refresh() },
        )

        SectionHeader("Sites et URL bloqués")
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Motif (ex. pornhub.com)") },
                singleLine = true,
            )
            OutlinedButton(onClick = {
                val p = input.trim()
                if (p.isNotEmpty()) { store.blockWeb(p); input = ""; refresh() }
            }) { Text("Bloquer") }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(snap.blockedWeb.sorted(), key = { "web:$it" }) { pattern ->
                val pendingAt = snap.pendingWebRemoval[pattern]
                ToggleRow(
                    leading = { WebGlyph() },
                    title = pattern,
                    subtitle = "Refusé dans le navigateur",
                    checked = pendingAt == null,
                    pending = pendingAt?.let { "Retrait ${countdown(it)}" },
                    onChange = { _ -> store.unblockWeb(pattern); refresh() },
                )
            }
        }
    }
}

@Composable
private fun WebGlyph() = Box(
    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
    Alignment.Center,
) {
    Icon(Icons.Filled.Home, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
}

// ---------------------------------------------------------------------------
// Onglet Réglages : statut + délai de réflexion.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var custom by rememberSaveable { mutableStateOf("") }
    var customError by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { StatusCard(snap) }
        item { SectionHeader("Délai de réflexion") }
        item {
            Text(
                "Actuel : ${Config.formatDelay(snap.delayMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp, 0.dp),
            )
        }
        snap.pendingDelay?.let { (v, at) ->
            item {
                Box(Modifier.padding(16.dp, 2.dp)) {
                    Text(
                        "→ passe à ${Config.formatDelay(v)} ${countdown(at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (choice in Config.delayChoicesMinutes) {
                    FilterChip(
                        selected = choice == snap.delayMinutes,
                        onClick = { store.requestDelay(choice); refresh() },
                        label = { Text(Config.formatDelay(choice)) },
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit); customError = false },
                    modifier = Modifier.width(160.dp),
                    label = { Text("Perso (min)") },
                    isError = customError,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedButton(onClick = {
                    val m = custom.toLongOrNull()
                    if (m == null) customError = true else { store.requestDelay(m); custom = ""; refresh() }
                }) { Text("Appliquer") }
            }
        }
        item {
            Text(
                "Augmenter le délai (ou bloquer une app, forcer une extension) prend effet " +
                    "tout de suite. Le baisser — ou débloquer, retirer une extension — ne prend " +
                    "effet qu'après le délai courant : en attendant, ça reste verrouillé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Briques communes.
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
)

@Composable
private fun StatusCard(snap: Snapshot) {
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
private fun OwnerWarning(status: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
    }
}
