package com.tstfaasaopwsw

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One row in the services list: the app side ([packages], [icon], [installed]) and the
 * web side ([domain]) of the same product, grouped. [icon] is null when no package is
 * installed (the list then shows a fallback), so a catalog service still appears and its
 * web rules can be set ahead of installing the app.
 */
internal data class ServiceInfo(
    val key: String,
    val label: String,
    val icon: ImageBitmap?,
    val packages: List<String>,
    val domain: String?,
    val installed: Boolean,
    val editable: Boolean,
    val customRules: List<String> = emptyList(),
) {
    /** A user-defined service with no installed app can be deleted outright. */
    val deletable: Boolean get() = editable && !installed
}

/**
 * The services list: the fixed [Config.catalog] (shown installed or not), plus every
 * other launchable installed app, plus any blocked web pattern that matches no catalog
 * service (so hand-added sites remain visible and editable). Sorted by label.
 */
internal suspend fun loadServices(context: Context): List<ServiceInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val self = context.packageName
    val store = Store.get(context)
    val defs = store.serviceDefs()
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
    val covered = defs.flatMap { it.packages }.toSet()

    val userRules = store.userRules()
    val defDomains = defs.mapNotNull { it.domain }.toSet()
    fun rulesFor(domain: String) =
        userRules.filter { UrlRules.host(it) == domain && UrlRules.path(it).isNotEmpty() }

    val fromDefs = defs.map { d ->
        val installedPkg = d.packages.firstOrNull { it.isInstalled(pm) }
        ServiceInfo(d.id, d.label, installedPkg?.iconOf(pm), d.packages, d.domain,
            installedPkg != null, true, d.domain?.let { rulesFor(it) } ?: emptyList())
    }
    // Launchable apps, plus any blocked app: a blocked app is hidden+suspended, so it drops
    // out of the launcher query — listing it anyway keeps a handle to unblock it.
    val appPkgs = ((launchable + store.blocked) - covered - self)
    val fromApps = appPkgs.mapNotNull { pkg ->
        runCatching {
            val ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            ServiceInfo(pkg, pm.getApplicationLabel(ai).toString(),
                pm.getApplicationIcon(ai).toSquareBitmap(), listOf(pkg), null, true, false)
        }.getOrNull()
    }
    // Blocked sites and user rules whose host is not a catalog service: shown on their own.
    val orphanHosts = (store.blockedWeb + userRules).map { UrlRules.host(it) }.toSet() - defDomains
    val fromWeb = orphanHosts.filter { it.isNotEmpty() }.map { host ->
        ServiceInfo(host, host, null, emptyList(), host, false, false, rulesFor(host))
    }
    (fromDefs + fromApps + fromWeb).sortedBy { it.label.lowercase() }
}

// MATCH_UNINSTALLED_PACKAGES so a frozen (hidden) app still counts as installed.
private fun String.isInstalled(pm: PackageManager): Boolean =
    runCatching { pm.getApplicationInfo(this, PackageManager.MATCH_UNINSTALLED_PACKAGES); true }.getOrDefault(false)

private fun String.iconOf(pm: PackageManager): ImageBitmap? =
    runCatching { pm.getApplicationIcon(this).toSquareBitmap() }.getOrNull()

private fun Drawable.toSquareBitmap(px: Int = 96): ImageBitmap =
    toBitmap(width = px, height = px).asImageBitmap()
