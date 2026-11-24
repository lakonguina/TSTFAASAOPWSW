package com.tstfaasaopwsw

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * Per-app controls. Blocking the app is always offered; when the app has a known site
 * ([Config.serviceDomain]) the site block and its hideable surfaces (feed, navbar) are
 * offered too. Each toggle drives the same real policy as the Apps/Web tabs.
 */
@Composable
internal fun AppDetailScreen(app: AppInfo, snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val domain = Config.serviceDomain[app.pkg]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon, null, Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                Text(app.pkg, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionHeader("Application")
        val unblockAt = snap.pendingUnblock[app.pkg]
        ToggleRow(
            leading = { Image(app.icon, null, Modifier.size(40.dp)) },
            title = "Block the app",
            subtitle = "Hidden, frozen, and kept from reinstalling",
            checked = app.pkg in snap.blocked && unblockAt == null,
            pending = unblockAt?.let { "Unblock ${countdown(it)}" },
            onChange = { on -> if (on) store.block(app.pkg) else store.unblock(app.pkg); refresh() },
        )

        if (domain != null) {
            SectionHeader("Website — $domain")
            val removalAt = snap.pendingWebRemoval[domain]
            ToggleRow(
                leading = { WebGlyph() },
                title = "Block the whole site",
                subtitle = "Refused in the browser (and phone-wide if the firewall is on)",
                checked = domain in snap.blockedWeb && removalAt == null,
                pending = removalAt?.let { "Unblock ${countdown(it)}" },
                onChange = { on -> if (on) store.blockWeb(domain) else store.unblockWeb(domain); refresh() },
            )
            val surfaces = Config.elementsFor(domain)
            if (surfaces.isNotEmpty()) SectionHeader("Hide surfaces")
            surfaces.forEach { rule ->
                ToggleRow(
                    leading = { WebGlyph() },
                    title = rule.name,
                    subtitle = rule.site,
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
