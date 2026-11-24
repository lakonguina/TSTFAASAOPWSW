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
)

/**
 * The services list: the fixed [Config.catalog] (shown installed or not), plus every
 * other launchable installed app, plus any blocked web pattern that matches no catalog
 * service (so hand-added sites remain visible and editable). Sorted by label.
 */
internal suspend fun loadServices(context: Context): List<ServiceInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val self = context.packageName
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.toSet()
    val cataloged = Config.catalog.flatMap { it.packages }.toSet()

    val fromCatalog = Config.catalog.map { s ->
        val installedPkg = s.packages.firstOrNull { it.isInstalled(pm) }
        ServiceInfo(s.domain ?: s.packages.first(), s.label, installedPkg?.iconOf(pm),
            s.packages, s.domain, installedPkg != null)
    }
    val fromApps = ((launchable - cataloged) - self).mapNotNull { pkg ->
        runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            ServiceInfo(pkg, pm.getApplicationLabel(ai).toString(),
                pm.getApplicationIcon(ai).toSquareBitmap(), listOf(pkg), null, true)
        }.getOrNull()
    }
    val knownDomains = Config.catalog.mapNotNull { it.domain }.toSet()
    val fromWeb = (Store.get(context).blockedWeb - knownDomains).map { d ->
        ServiceInfo(d, d, null, emptyList(), d, false)
    }
    (fromCatalog + fromApps + fromWeb).sortedBy { it.label.lowercase() }
}

private fun String.isInstalled(pm: PackageManager): Boolean =
    runCatching { pm.getApplicationInfo(this, 0); true }.getOrDefault(false)

private fun String.iconOf(pm: PackageManager): ImageBitmap? =
    runCatching { pm.getApplicationIcon(this).toSquareBitmap() }.getOrNull()

private fun Drawable.toSquareBitmap(px: Int = 96): ImageBitmap =
    toBitmap(width = px, height = px).asImageBitmap()
