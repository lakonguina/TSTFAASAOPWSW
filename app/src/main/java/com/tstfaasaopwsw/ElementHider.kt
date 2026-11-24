package com.tstfaasaopwsw

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Hides page elements via one tiny built-in web extension per catalog rule (static
 * content-script CSS, hidden before paint, no reload, survives SPA re-renders). A toggle
 * just enables or disables the matching extension — no app <-> extension messaging, which
 * GeckoView does not support on Android ("Native manifests are not supported").
 *
 * A toggle takes effect on the next page load of the site.
 */
object ElementHider {
    private var controller: WebExtensionController? = null
    private val exts = mutableMapOf<String, WebExtension>()

    fun install(runtime: GeckoRuntime, store: Store) {
        val c = runtime.webExtensionController
        controller = c
        val wanted = Config.elementCatalog.map { it.geckoId }.toSet()
        // Drop our own stale extensions left in GeckoView's profile by earlier versions.
        c.list().accept { installed ->
            installed?.forEach { ext ->
                if (ext.id.endsWith("@tstfaasaopwsw") && ext.id !in wanted) runCatching { c.uninstall(ext) }
            }
            for (r in Config.elementCatalog) {
                c.ensureBuiltIn(r.uri, r.geckoId).accept { ext ->
                    if (ext != null) { exts[r.id] = ext; applyOne(r.id, store) }
                }
            }
        }
    }

    /** Align each extension's enabled state with the store (call on toggle change). */
    fun sync(store: Store) {
        for (r in Config.elementCatalog) applyOne(r.id, store)
    }

    private fun applyOne(id: String, store: Store) {
        val c = controller ?: return
        val ext = exts[id] ?: return
        val result = if (id in store.effectiveEnabledElements()) {
            c.enable(ext, WebExtensionController.EnableSource.USER)
        } else {
            c.disable(ext, WebExtensionController.EnableSource.USER)
        }
        result.accept { updated -> if (updated != null) exts[id] = updated }
    }
}
