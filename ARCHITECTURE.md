# Architecture — ForceList

*Vue abstraite. Comment l'app fonctionne, sans entrer dans le code.*

## Ce que l'app est

Un outil d'auto-discipline. Tu poses des règles sur **ton propre** téléphone pour ne
pas pouvoir les défaire sur un coup de tête. L'app en est le gardien : elle est
**Device Owner** (le rôle d'administration le plus fort d'Android, posé une fois via
`adb`), ce qui lui donne le droit de bloquer des apps, verrouiller des réglages et
filtrer le réseau.

## Le principe unique : l'engagement à sens unique

Toute règle suit la même loi, façon Pluckeye :

- **Serrer** (bloquer, ajouter un filtre, allonger un délai) → **immédiat**.
- **Desserrer** (débloquer, retirer un filtre, raccourcir un délai) → **seulement après
  le délai de réflexion** en cours. En attendant, ça reste verrouillé, et un compte à
  rebours le montre.

C'est le cœur. Tout le reste n'est que de l'exécution.

## Les trois écrans (tout ce que l'utilisateur voit)

1. **Apps** — une liste avec un interrupteur par app : bloquée / autorisée. Plus le
   contrôle des installations.
2. **Web** — des listes : sites interdits, morceaux de page à cacher, sites « sous
   contrôle » (accessibles seulement via le navigateur de l'app).
3. **Délais** — le réglage du délai de réflexion.

Pas d'intelligence derrière : juste des **toggles et des listes**. Chaque ligne appelle
du code fixe qui active la bonne mécanique. L'usine en dessous est invisible.

## Comment ça marche dedans

**Une seule source de vérité : l'intention.** L'app stocke ce que tu *veux* (règles +
attentes en cours), pas l'état de la machine. Une fonction unique, **`reconcile`**,
est rejouée à chaque ouverture, à chaque échéance de délai (alarme) et au redémarrage.
Elle fait mûrir les attentes échues, puis pousse l'intention vers l'appareil. Elle est
**idempotente** : la rejouer dix fois ne change rien, elle ne fait que réparer l'écart.

**Trois surfaces d'exécution, un seul cerveau :**

| Surface | Rôle | Portée |
|---|---|---|
| **Device Owner** | bloquer/geler des apps, filtrer les installations, verrouiller les portes de sortie | tout le téléphone |
| **Pare-feu réseau** (VPN local) | bloquer un **domaine** ; réserver un site à notre navigateur | toutes les apps |
| **Navigateur + filtre** | bloquer un **chemin** précis, cacher des morceaux de page | notre navigateur |

Règle de répartition : blocage **par domaine** = pare-feu (partout) ; **par chemin** ou
**cosmétique** = navigateur (chez nous seulement). Un même blocage n'est jamais posé à
deux endroits.

**L'invariant clé.** Un site « sous contrôle » (limité dans le temps, feed masqué…)
n'est atteignable **que par notre navigateur** : le pare-feu le ferme à toutes les
autres apps. Sinon le contrôle se contournerait via la fenêtre web d'une autre app.

## Les portes de sortie

Fermées autant que possible : désinstallation bloquée, démarrage « sans échec »
interdit, pare-feu verrouillé en always-on. **La seule issue reste un PC + `adb`** —
friction volontaire, et plafond assumé de tout le système.
