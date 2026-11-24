package com.tstfaasaopwsw

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Apps tab: the services list (app + web grouped); tapping one opens its controls. */
@Composable
internal fun ServicesScreen(services: List<ServiceInfo>?, snap: Snapshot, refresh: () -> Unit, onOpen: (ServiceInfo) -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var site by rememberSaveable { mutableStateOf("") }

    val q = site.trim()
    val filtered = services?.filter { q.isEmpty() || it.label.contains(q, true) || it.domain?.contains(q, true) == true }

    Column(Modifier.fillMaxSize()) {
        if (!snap.isDeviceOwner) OwnerWarning(snap.statusText)
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = site,
                onValueChange = { site = it },
                modifier = Modifier.weight(1f),
                label = { Text("Filter, or block a site (e.g. pornhub.com)") },
                singleLine = true,
            )
            OutlinedButton(onClick = {
                if (q.isNotEmpty()) { store.blockWeb(q); site = ""; refresh() }
            }) { Text("Block") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (filtered == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            } else {
                items(filtered, key = { "svc:${it.key}" }) { s ->
                    val appOn = s.packages.isNotEmpty() && s.packages.all { it in snap.blocked }
                    val webOn = s.domain != null && s.domain in snap.blockedWeb
                    ListItem(
                        modifier = Modifier.clickable { onOpen(s) },
                        leadingContent = { ServiceIcon(s, 40.dp) },
                        headlineContent = { Text(s.label) },
                        supportingContent = { Text(statusLine(s, appOn, webOn)) },
                        trailingContent = { Icon(Icons.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
        }
    }
}

private fun statusLine(s: ServiceInfo, appOn: Boolean, webOn: Boolean): String {
    val app = when {
        s.packages.isEmpty() -> null
        appOn -> "App blocked"
        s.installed -> "App allowed"
        else -> "Not installed"
    }
    val web = if (s.domain != null) (if (webOn) "site blocked" else "site allowed") else null
    return listOfNotNull(app, web).joinToString(" · ")
}

/** App icon when installed, else a lettered circle fallback (web-only / not-installed services). */
@Composable
internal fun ServiceIcon(service: ServiceInfo, size: Dp) {
    val icon = service.icon
    if (icon != null) {
        Image(icon, null, Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            Alignment.Center,
        ) {
            Text(
                service.label.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}
