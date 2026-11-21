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

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                locationUri: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                url = locationUri ?: ""
                locationUri?.let { store.addHistory(it) }
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
                val blocked = store.blockedWeb.any { request.uri.contains(it, ignoreCase = true) }
                return GeckoResult.fromValue(if (blocked) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
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
}
