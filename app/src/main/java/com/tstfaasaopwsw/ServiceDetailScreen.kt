package com.tstfaasaopwsw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
@Composable
internal fun ServiceDetailScreen(service: ServiceInfo, snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(service, 48.dp)
            Spacer(Modifier.width(16.dp))
            Text(service.label, style = MaterialTheme.typography.titleLarge)
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
                ToggleRow(
                    leading = { WebGlyph() },
                    title = rule.name,
                    subtitle = "Hide on ${rule.site}",
                    checked = rule.id in snap.enabledElements,
                    pending = null,
                    onChange = { on ->
                        if (on) store.enableElement(rule.id) else store.disableElement(rule.id)
                        ElementHider.sync(store); refresh()
                    },
                )
            }
        }
    }
}
