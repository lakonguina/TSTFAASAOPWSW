package com.tstfaasaopwsw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Per-service controls, grouped: an "Application" block (present when the service has a
 * package) and a "Web" block (present when it has a domain). The web block gathers the
 * whole-site block and the hideable surfaces (feed, navbar) under one heading. Each row
 * carries its block switch and a per-facet daily schedule (allowed hours + minutes). The
 * service name and its edit/delete actions live in the top bar, not here.
 */
@Composable
internal fun ServiceDetailScreen(service: ServiceInfo, snap: Snapshot, refresh: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (service.packages.isNotEmpty()) {
            SectionHeader("Application")
            val unblockAt = service.packages.firstNotNullOfOrNull { snap.pendingUnblock[it] }
            RuleRow(
                key = ScheduleKeys.app(service.packages.first()),
                title = "Block the app",
                checked = service.packages.all { it in snap.blocked } && unblockAt == null,
                pending = unblockAt?.let { "Unblock ${countdown(it)}" },
                onChange = { on ->
                    service.packages.forEach { if (on) store.block(it) else store.unblock(it) }; refresh()
                },
                snap = snap, refresh = refresh,
            )
        }

        val domain = service.domain
        if (domain != null) {
            WebSectionHeader(domain, refresh)
            val removalAt = snap.pendingWebRemoval[domain]
            RuleRow(
                key = ScheduleKeys.web(domain),
                title = "Block the whole site",
                checked = domain in snap.blockedWeb && removalAt == null,
                pending = removalAt?.let { "Unblock ${countdown(it)}" },
                onChange = { on -> if (on) store.blockWeb(domain) else store.unblockWeb(domain); refresh() },
                snap = snap, refresh = refresh,
            )
            Config.elementsFor(domain).forEach { rule ->
                val elemAt = snap.pendingElement[rule.id]
                RuleRow(
                    key = ScheduleKeys.elem(rule.id),
                    title = rule.name,
                    checked = rule.id in snap.enabledElements && elemAt == null,
                    pending = elemAt?.let { "Show again ${countdown(it)}" },
                    onChange = { on ->
                        if (on) store.enableElement(rule.id) else store.disableElement(rule.id)
                        ElementHider.sync(store); refresh()
                    },
                    snap = snap, refresh = refresh,
                )
            }
            service.customRules.sorted().forEach { pattern ->
                val removalAt = snap.pendingWebRemoval[pattern]
                RuleRow(
                    key = ScheduleKeys.web(pattern),
                    title = UrlRules.path(pattern).ifEmpty { pattern },
                    checked = pattern in snap.blockedWeb && removalAt == null,
                    pending = removalAt?.let { "Unblock ${countdown(it)}" },
                    onChange = { on -> if (on) store.blockWeb(pattern) else store.unblockWeb(pattern); refresh() },
                    snap = snap, refresh = refresh,
                    onDelete = { store.deleteUserRule(pattern); PolicyManager.reconcile(context); refresh() },
                )
            }
        }
    }
}
