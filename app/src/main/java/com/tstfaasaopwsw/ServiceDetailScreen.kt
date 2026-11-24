package com.tstfaasaopwsw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

/**
 * Per-service controls, grouped: an "Application" block (present when the service has a
 * package) and a "Web" block (present when it has a domain). The web block gathers the
 * whole-site block and the hideable surfaces (feed, navbar) under one heading. Each
 * toggle drives the same real policy as before; a not-installed service can still be
 * web-configured and its app pre-blocked against future install.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceDetailScreen(service: ServiceInfo, snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    var editing by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 8.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(service, 48.dp)
            Spacer(Modifier.width(16.dp))
            Text(service.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (service.editable) {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit", tint = tint) }
                if (service.deletable) IconButton(onClick = { store.removeService(service.key); refresh() }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = tint)
                }
            }
        }

        if (service.packages.isNotEmpty()) {
            SectionHeader("Application")
            val unblockAt = service.packages.firstNotNullOfOrNull { snap.pendingUnblock[it] }
            ToggleRow(
                leading = { ServiceIcon(service, 40.dp) },
                title = "Block the app",
                subtitle = if (service.installed) "Hidden, frozen, and kept from reinstalling"
                else "Not installed — blocks any future install",
                checked = service.packages.all { it in snap.blocked } && unblockAt == null,
                pending = unblockAt?.let { "Unblock ${countdown(it)}" },
                onChange = { on ->
                    service.packages.forEach { if (on) store.block(it) else store.unblock(it) }; refresh()
                },
            )
        }

        val domain = service.domain
        if (domain != null) {
            SectionHeader("Web — $domain")
            val removalAt = snap.pendingWebRemoval[domain]
            ToggleRow(
                leading = { WebGlyph() },
                title = "Block the whole site",
                subtitle = "Refused in the browser (and phone-wide if the filter is on)",
                checked = domain in snap.blockedWeb && removalAt == null,
                pending = removalAt?.let { "Unblock ${countdown(it)}" },
                onChange = { on -> if (on) store.blockWeb(domain) else store.unblockWeb(domain); refresh() },
            )
            Config.elementsFor(domain).forEach { rule ->
                val elemAt = snap.pendingElement[rule.id]
                ToggleRow(
                    leading = { WebGlyph() },
                    title = rule.name,
                    subtitle = "Hide on ${rule.site}",
                    checked = rule.id in snap.enabledElements && elemAt == null,
                    pending = elemAt?.let { "Show again ${countdown(it)}" },
                    onChange = { on ->
                        if (on) store.enableElement(rule.id) else store.disableElement(rule.id)
                        ElementHider.sync(store); refresh()
                    },
                )
            }
        }
    }

    if (editing) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { editing = false }, sheetState = sheetState) {
            ServiceForm(
                initial = ServiceDef(service.key, service.label, service.packages, service.domain),
                onSave = { def -> store.upsertService(def); editing = false; refresh() },
            )
        }
    }
}
