# Spec — TSTFAASAOPWSW

App Android d'auto-discipline, tournant en **Device Owner** sur son propre
téléphone. On pose des règles sur ses apps et ses sites pour ne pas pouvoir les
défaire sur un coup de tête.

## Stack
- Kotlin natif, Jetpack Compose (Material 3), minSdk 24.
- Un seul `DeviceAdminReceiver` (`AdminReceiver`) comme composant admin.
- Navigateur embarqué GeckoView, imposé comme navigateur par défaut.
- Provisionnement : `adb shell dpm set-device-owner <pkg>/.AdminReceiver` sur un
  téléphone fraîchement réinitialisé (aucun compte Google).

## Principe unique : l'engagement à sens unique (façon Pluckeye)
- **Serrer** (bloquer, ajouter une règle, rétrécir une plage, allonger le délai) →
  **immédiat**.
- **Desserrer** (débloquer, supprimer une règle, raccourcir le délai) → **différé du
  délai de réflexion** en cours ; ça reste verrouillé entre-temps, avec compte à rebours.
- Une seule source de vérité : l'**intention** (règles + attentes en cours). Une fonction
  idempotente `PolicyManager.reconcile` est rejouée à l'ouverture, à chaque échéance
  d'alarme et au redémarrage ; elle fait mûrir les attentes échues (`PendingQueue`) puis
  pousse l'intention vers l'appareil.

## Modèle : les services
- Un **service** regroupe sous un nom les **packages** de l'app et le **domaine** du site
  d'un même produit (ex. Twitter/X = `com.twitter.android` + `x.com`).
- Les services sont des données **éditables** (`ServiceDef`), seedées une fois depuis
  `Config.catalog`. On peut en **créer**, **renommer**, changer package/domaine, et
  **supprimer** (sauf si l'app est installée).
- Un service s'affiche **même si l'app n'est pas installée**, pour pré-régler le web à
  l'avance ; son app peut être pré-bloquée contre toute installation future.
- La liste des services = `ServiceDef` + apps installées non cataloguées + hôtes de sites
  bloqués hors catalogue. Logo = favicon du site (téléchargé de `https://<domaine>/favicon.ico`),
  sinon icône système, sinon initiale.

## Deux écrans
1. **Apps** — la liste des services. Un `+` permet de bloquer une URL (rangée
   automatiquement sous le service dont le domaine correspond) ou de créer un service.
2. **General** — statut Device Owner + **délai de réflexion**.

## Page d'un service
Nom + icônes **Edit/Delete** dans la barre du haut. Deux blocs :
- **Application** : « Block the app » (masqué + gelé + réinstall empêchée ; si non
  installée, bloque l'install future).
- **Web — <domaine>** (avec un `+` pour ajouter une règle) :
  - « Block the whole site » (règle système),
  - les surfaces masquables (feed, navbar) — règles système,
  - les **règles custom** de l'utilisateur (URL précises, ex. `x.com/settings/...`),
    chacune éditable/supprimable ; les règles système ne le sont pas.

Chaque règle (app, site, feed, custom) porte :
- un **switch** de blocage,
- une **plage horaire** quotidienne optionnelle (accès autorisé seulement dans la fenêtre,
  bloqué sinon) — **appliquée**,
- un **quota de minutes/jour** optionnel — **stocké et affiché, pas encore appliqué**.
Plage + quota se règlent dans une sheet ouverte par l'icône horloge ; le résumé s'affiche
en petit sous le titre.

## Trois surfaces d'exécution, un seul cerveau
| Surface | Rôle | Portée |
|---|---|---|
| **Device Owner** | masquer/geler des apps, filtrer les installations, verrouiller les portes de sortie | tout le téléphone |
| **Pare-feu réseau** (VPN local, DNS) | bloquer un **domaine** | toutes les apps |
| **Navigateur + extensions** | bloquer un **chemin** précis, masquer des morceaux de page | notre navigateur |

- Le pare-feu est **toujours actif** (non désactivable) ; `reconcile` le force.
- Les plages horaires se replient dans des **sets effectifs** (`effectiveBlockedApps`,
  `effectiveBlockedWeb`, `effectiveEnabledElements`) que ces trois surfaces appliquent
  déjà ; une alarme est reprogrammée à la prochaine borne de fenêtre.

## Mise à jour
- Auto-update depuis GitHub Releases : push d'un tag `vN`, la CI build/signe/publie, l'app
  lit `releases/latest` et s'installe silencieusement (Device Owner).
- Le téléphone de dev tourne en build **debug** (clé `debug.keystore`) ≠ clé de la CI, donc
  l'auto-update ne le touche pas ; on y pousse par `adb install -r` d'un build debug.

## Sortie de secours
`adb shell dpm remove-active-admin <pkg>/.AdminReceiver`

## Reste à faire
- **Quota de minutes/jour** : mesure d'usage réel (chrono navigateur pour le web,
  `UsageStatsManager` pour les apps, avec accès « Usage access » activé une fois et reset
  quotidien), décompte « temps restant » affiché, blocage à épuisement.
