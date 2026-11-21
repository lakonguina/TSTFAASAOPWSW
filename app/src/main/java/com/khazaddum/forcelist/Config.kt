package com.khazaddum.forcelist

/** Constantes fixes. Les listes (apps bloquées, extensions, délai) sont désormais
 *  dynamiques et vivent dans [Store], éditables depuis l'app. */
object Config {

    /** Navigateur qui applique `ExtensionInstallForcelist`. Edge honore cette policy. */
    const val targetBrowser = "com.microsoft.emmx"

    /**
     * Dépôt GitHub PUBLIC d'où l'app se met à jour toute seule (OWNER/REPO).
     * L'updater lit `releases/latest` et installe l'APK si le tag `vN` dépasse
     * [BuildConfig.VERSION_CODE]. À corriger si le dépôt change.
     */
    const val githubRepo = "lakonguina/khazad-dum"

    /** URL de mise à jour des extensions Edge, collée après le `;`. */
    const val EDGE_UPDATE_URL = "https://edge.microsoft.com/extensionwebstorebase/v1/crx"

    /** Options rapides du sélecteur de délai (en minutes) ; un champ libre permet tout le reste. */
    val delayChoicesMinutes: List<Long> = listOf(0, 5, 10, 20, 30, 60, 120, 360, 720, 1440)

    fun formatDelay(min: Long): String = when {
        min <= 0L -> "aucun"
        min == 1L -> "1 min (test)"
        min < 60L -> "$min min"
        min % 60L == 0L -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60}"
    }

    /** Une extension forçable : [id] du store Edge + libellé pour l'UI. */
    data class ExtensionInfo(val id: String, val name: String, val description: String) {
        /** Valeur stockée dans [Store] : "id;updateUrl". */
        val value: String get() = "$id;$EDGE_UPDATE_URL"
    }

    /**
     * Extensions connues proposées en raccourci quand on veut en ajouter une (on
     * évite d'avoir à trouver l'ID). Ce ne sont PAS des extensions déjà forcées :
     * la liste réelle vit dans [Store.extensions]. IDs vérifiés sur le store Edge.
     */
    val extensionCatalog: List<ExtensionInfo> = listOf(
        ExtensionInfo(
            "odfafepnkmbhccpbejgmiehpchacaeak",
            "uBlock Origin",
            "Bloqueur de pubs et de contenu, filtres personnalisables.",
        ),
        ExtensionInfo(
            "hnncfhodpmpjchmmcnimoimkcojdmfhl",
            "LeechBlock NG",
            "Bloque des sites selon des horaires et des quotas.",
        ),
        ExtensionInfo(
            "caoacbimdbbljakfhgikoodekdnlcgpk",
            "DuckDuckGo Privacy Essentials",
            "Protection anti-pistage et confidentialité.",
        ),
    )

    /** Nom lisible d'une extension si on la connaît, sinon null (on affichera l'ID). */
    fun extensionName(id: String): String? = extensionCatalog.firstOrNull { it.id == id }?.name

    private val EXT_ID = Regex("[a-p]{32}")

    /**
     * Extrait un ID d'extension depuis une saisie : soit l'ID brut, soit une URL
     * du store Edge collée (…/addons/detail/<slug>/<id>). Renvoie null si introuvable.
     */
    fun parseExtensionId(input: String): String? =
        EXT_ID.find(input.trim().lowercase())?.value
}
