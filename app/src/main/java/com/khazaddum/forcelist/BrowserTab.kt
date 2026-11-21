package com.khazaddum.forcelist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/** Un onglet : une session GeckoView + son état observable par l'UI, et le filtre d'URL. */
internal class Tab(runtime: GeckoRuntime, store: Store) {
    val session = GeckoSession()
    var url by mutableStateOf(BrowserActivity.HOME)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blocked by mutableStateOf<String?>(null) // URL refusée à afficher, sinon null

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                locationUri: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                // Attrape aussi les navigations d'une SPA (ex. X/Twitter via pushState),
                // qui ne passent PAS par onLoadRequest : ici on réagit après coup.
                url = locationUri ?: ""
                val hit = locationUri != null && store.blockedWeb.any { UrlRules.matchesUrl(locationUri, it) }
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
                val hit = store.blockedWeb.any { UrlRules.matchesUrl(request.uri, it) }
                if (hit) blocked = request.uri
                return GeckoResult.fromValue(if (hit) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
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
        }
        session.open(runtime)
    }

    fun load(input: String) = session.loadUri(BrowserActivity.normalize(input))
    fun close() = session.close()

    /** Quitte une page bloquée : revient en arrière, ou charge l'accueil si impossible. */
    fun leaveBlocked() {
        if (canGoBack) session.goBack() else { load(BrowserActivity.HOME); blocked = null }
    }
}
