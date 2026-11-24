package com.tstfaasaopwsw

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Apps tab: the list of all apps; tapping one opens its per-app controls. */
@Composable
internal fun AppsScreen(apps: List<AppInfo>?, snap: Snapshot, refresh: () -> Unit, onOpen: (AppInfo) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }

    val q = query.trim()
    fun matches(vararg fields: String) = q.isEmpty() || fields.any { it.contains(q, true) }

    val filteredApps = apps?.filter { matches(it.label, it.pkg) }

    Column(Modifier.fillMaxSize()) {
        if (!snap.isDeviceOwner) OwnerWarning(snap.statusText)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 8.dp),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search an app") },
            singleLine = true,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (filteredApps == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            } else {
                items(filteredApps, key = { "app:${it.pkg}" }) { app ->
                    val blocked = app.pkg in snap.blocked
                    ListItem(
                        modifier = Modifier.clickable { onOpen(app) },
                        leadingContent = { Image(app.icon, null, Modifier.size(40.dp)) },
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(if (blocked) "Blocked" else "Allowed") },
                        trailingContent = { Icon(Icons.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
        }
    }
}
