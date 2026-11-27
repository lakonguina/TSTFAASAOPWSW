package com.tstfaasaopwsw

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Apps tab: the services list (app + web grouped); tapping one opens its controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServicesScreen(services: List<ServiceInfo>?, snap: Snapshot, refresh: () -> Unit, onOpen: (ServiceInfo) -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    val q = query.trim()
    val filtered = services?.filter { q.isEmpty() || it.label.contains(q, true) || it.domain?.contains(q, true) == true }

    val insets = GlassContentInsets
    Column(Modifier.fillMaxSize()) {
        GlassLargeTitle("Apps", Modifier.padding(top = insets.calculateTopPadding())) {
            FilledTonalIconButton(onClick = { sheetOpen = true }) {
                Icon(Icons.Filled.Add, "Block a site")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(Space.lg, Space.xs, Space.lg, Space.sm),
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search a service") },
            singleLine = true,
            shape = GlassShape.field,
        )
        if (!snap.isDeviceOwner) OwnerWarning(snap.statusText)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = insets.calculateBottomPadding()),
        ) {
            if (filtered == null) {
                item { Box(Modifier.fillMaxWidth().padding(Space.xxl), Alignment.Center) { CircularProgressIndicator() } }
            } else {
                items(filtered, key = { "svc:${it.key}" }) { s ->
                    val appOn = s.packages.isNotEmpty() && s.packages.all { it in snap.blocked }
                    val webOn = s.domain != null && s.domain in snap.blockedWeb
                    ListItem(
                        modifier = Modifier.clickable { onOpen(s) },
                        leadingContent = { ServiceIconWithStatus(s, 40.dp) },
                        headlineContent = { Text(s.label) },
                        supportingContent = { Text(statusLine(s, appOn, webOn)) },
                        trailingContent = { Icon(Icons.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        AddSheet(
            onDismiss = { sheetOpen = false },
            onBlockUrl = { p -> store.addUserRule(p); PolicyManager.reconcile(context); sheetOpen = false; refresh() },
            onNewService = { def -> store.upsertService(def); sheetOpen = false; refresh() },
        )
    }
}

/** The Apps-list "+" sheet: block a URL (auto-filed under the matching service) or create a service. */
@Composable
private fun AddSheet(onDismiss: () -> Unit, onBlockUrl: (String) -> Unit, onNewService: (ServiceDef) -> Unit) {
    var newService by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    GlassBottomSheet(onDismiss = onDismiss, title = if (newService) "New service" else "Block a URL or site") {
        if (newService) {
            ServiceForm(initial = null, onSave = onNewService)
        } else {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(),
                label = { Text("URL or domain (e.g. x.com/settings)") }, singleLine = true, shape = GlassShape.field)
            Spacer(Modifier.height(Space.lg))
            Button(
                onClick = { val p = url.trim(); if (p.isNotEmpty()) onBlockUrl(p) },
                modifier = Modifier.fillMaxWidth(),
                shape = GlassShape.pill,
            ) { Text("Block") }
            Spacer(Modifier.height(Space.xs))
            TextButton(onClick = { newService = true }, modifier = Modifier.fillMaxWidth()) {
                Text("New service instead")
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

/**
 * The service icon with an install badge: a green dot when the app is installed, red when
 * not. Only for services that have an app side; web-only services carry no badge.
 */
@Composable
internal fun ServiceIconWithStatus(service: ServiceInfo, size: Dp) {
    Box {
        ServiceIcon(service, size)
        if (service.packages.isNotEmpty()) {
            val color = if (service.installed) Color(0xFF34C759) else Color(0xFFFF3B30)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** The site's own favicon when available, else the installed app icon, else a lettered circle. */
@Composable
internal fun ServiceIcon(service: ServiceInfo, size: Dp) {
    val context = LocalContext.current
    val favicon by produceState<ImageBitmap?>(null, service.domain) {
        value = service.domain?.let { Favicons.of(context, it) }
    }
    val icon = favicon ?: service.icon
    if (icon != null) {
        // White tile behind the icon: most favicons are transparent PNGs designed for a light
        // background, so without this they show the dark sheet through and read as black blocks.
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color.White)) {
            Image(icon, null, Modifier.size(size))
        }
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
