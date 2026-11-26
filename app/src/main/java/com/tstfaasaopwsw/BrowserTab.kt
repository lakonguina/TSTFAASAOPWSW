package com.tstfaasaopwsw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** What a long-press landed on: a link and/or an image. Null fields when absent. */
internal data class ContextTarget(val link: String?, val image: String?, val title: String?)

/**
 * A tab: a GeckoView session + its UI-observable state, and the URL filter.
 *
 * [existing] wraps a session Gecko handed us from [onNewSession] (already opened by Gecko),
 * so we attach delegates but do NOT re-open it. [onNewTab] is called when the page asks for a
 * new window/tab (`target=_blank`, `window.open`): without it such links open nothing.
 */
internal class Tab(
    runtime: GeckoRuntime,
    store: Store,
    existing: GeckoSession? = null,
    private val onNewTab: ((GeckoSession) -> Unit)? = null,
    private val onDownload: ((String, String) -> Unit)? = null,
) {
    val session = existing ?: GeckoSession()
    var url by mutableStateOf(BrowserActivity.HOME)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blocked by mutableStateOf<String?>(null) // refused URL to show, otherwise null
    var scrollY by mutableIntStateOf(0) // vertical scroll; drives the Safari-style bar auto-hide
    var contextTarget by mutableStateOf<ContextTarget?>(null) // long-press menu target, else null
    var desktop by mutableStateOf(false) // desktop-site mode for this tab
    var thumbnail by mutableStateOf<android.graphics.Bitmap?>(null) // last snapshot, for the Tabs grid

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                locationUri: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                // Also catches SPA navigations (e.g. X/Twitter via pushState),
                // that do NOT go through onLoadRequest: here we react afterwards.
                url = locationUri ?: ""
                val hit = locationUri != null &&
                    (store.effectiveBlockedWeb().any { UrlRules.matchesUrl(locationUri, it) } || store.isUrlImportBlocked(locationUri))
                blocked = if (hit) locationUri else null
                if (!hit) locationUri?.let { store.addHistory(it) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@Tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                this@Tab.canGoForward = canGoForward
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val hit = store.effectiveBlockedWeb().any { UrlRules.matchesUrl(request.uri, it) } ||
                    store.isUrlImportBlocked(request.uri)
                if (hit) blocked = request.uri
                return GeckoResult.fromValue(if (hit) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
            }

            // A link that wants a new window/tab (target=_blank, window.open). Without this,
            // Gecko refuses the popup and the click does nothing (e.g. links on X/Twitter).
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val s = GeckoSession()
                onNewTab?.invoke(s) // host wraps it in a Tab and shows it; Gecko opens it
                return GeckoResult.fromValue(s)
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                this@Tab.progress = progress
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                this@Tab.title = title ?: ""
            }

            // Long-press on a link or image -> show our own context menu (see [Browser]).
            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                val link = element.linkUri
                val image = element.srcUri?.takeIf { element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE }
                if (link != null || image != null) contextTarget = ContextTarget(link, image, element.title)
            }

            // A response the engine can't display (PDF, zip, …) -> download it.
            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                val uri = response.uri ?: return
                val name = response.headers["Content-Disposition"]
                    ?.substringAfter("filename=", "")?.trim('"', ' ')?.takeIf { it.isNotEmpty() }
                    ?: android.net.Uri.parse(uri).lastPathSegment ?: "download"
                onDownload?.invoke(uri, name)
            }
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                this@Tab.scrollY = scrollY
            }
        }
        if (existing == null) session.open(runtime) // a wrapped new-session is opened by Gecko
    }

    fun load(input: String) = session.loadUri(BrowserActivity.normalize(input))
    fun close() = session.close()

    /** Toggles the desktop-site user agent + viewport for this tab, then reloads. */
    fun setDesktopMode(on: Boolean) {
        desktop = on
        session.settings.userAgentMode =
            if (on) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (on) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        session.reload()
    }

    /** Find-in-page: highlights and scrolls to the next (or previous) match of [query]. */
    fun find(query: String, forward: Boolean = true) {
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        runCatching { session.finder.find(query, flags) }
    }

    fun clearFind() = runCatching { session.finder.clear() }

    /** Leaves a blocked page: goes back, or loads home if not possible. */
    fun leaveBlocked() {
        if (canGoBack) session.goBack() else { load(BrowserActivity.HOME); blocked = null }
    }
}
