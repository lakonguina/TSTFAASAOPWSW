package com.tstfaasaopwsw

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/**
 * Loads the bundled "hider" web extension and feeds it the enabled element rules. Its
 * content script hides them by XPath (CSS display:none, no reload). The app <-> extension
 * link is a native port named "browser"; the extension connects, the app pushes rules.
 */
object ElementHider {
    private const val ID = "hider@tstfaasaopwsw"
    private var port: WebExtension.Port? = null

    fun install(runtime: GeckoRuntime, store: Store) {
        runtime.webExtensionController.ensureBuiltIn("resource://android/assets/hider/", ID)
            .accept { ext -> ext?.setMessageDelegate(delegate(store), "browser") }
    }

    /** Push the current enabled rules to the extension (call on toggle change). */
    fun sync(store: Store) {
        runCatching { port?.postMessage(payload(store)) }
    }

    private fun delegate(store: Store) = object : WebExtension.MessageDelegate {
        override fun onConnect(p: WebExtension.Port) {
            port = p
            runCatching { p.postMessage(payload(store)) }
        }
    }

    private fun payload(store: Store): JSONObject {
        val arr = JSONArray()
        val on = store.enabledElements
        for (r in Config.elementCatalog) if (r.id in on) {
            arr.put(JSONObject().put("site", r.site).put("xpath", r.xpath))
        }
        return JSONObject().put("rules", arr)
    }
}
