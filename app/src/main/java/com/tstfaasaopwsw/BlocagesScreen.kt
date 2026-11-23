package com.tstfaasaopwsw

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Onglet Apps : une seule liste = extensions forçables (en haut) puis toutes les apps. */
@Composable
internal fun BlocagesScreen(apps: List<AppInfo>?, snap: Snapshot, refresh: () -> Unit) {
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
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 8.dp),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Rechercher une app ou extension") },
            singleLine = true,
        )
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
