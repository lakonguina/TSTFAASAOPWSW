package com.tstfaasaopwsw

/**
 * Règles de blocage web, partagées par le pare-feu DNS et le navigateur maison.
 *
 * Un motif est un domaine (`x.com`) ou une URL (`https://x.com/settings`). Le DNS ne
 * peut filtrer qu'au domaine ; le navigateur filtre au chemin : bloquer `.../settings`
 * refuse `/settings` et ses sous-pages, mais pas `/home`. Un motif sans chemin (domaine
 * nu) bloque tout le domaine des deux côtés.
 */
object UrlRules {

    /** Hôte d'un motif ou d'une URL : sans schéma, chemin, query ni port. */
    fun host(s: String): String {
        val afterScheme = s.trim().lowercase().substringAfter("://", s.trim().lowercase())
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    }

    /** Chemin d'un motif ou d'une URL : commence par '/', ou "" si aucun. */
    fun path(s: String): String {
        val afterScheme = s.trim().lowercase().substringAfter("://", s.trim().lowercase())
        val afterHost = afterScheme.substringBefore('?').substringBefore('#')
        val slash = afterHost.indexOf('/')
        return if (slash < 0) "" else afterHost.substring(slash)
    }

    /** DNS : le domaine interrogé est l'hôte du motif ou un de ses sous-domaines. */
    fun matchesDomain(domain: String, pattern: String): Boolean {
        val h = host(pattern)
        return h.isNotEmpty() && (domain == h || domain.endsWith(".$h"))
    }

    /** Navigateur : même hôte (ou sous-domaine) ET chemin = celui du motif ou une sous-page. */
    fun matchesUrl(url: String, pattern: String): Boolean {
        if (!matchesDomain(host(url), pattern)) return false
        val rulePath = path(pattern)
        if (rulePath.isEmpty() || rulePath == "/") return true // domaine nu -> tout le site
        val urlPath = path(url).ifEmpty { "/" }
        return urlPath == rulePath || urlPath.startsWith("$rulePath/")
    }
}
