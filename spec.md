# Brief — App Android Device Owner "ForceList"

## But
App Android (Device Owner) qui force des extensions de navigateur + des apps
et les rend non-désinstallables. La liste est contrôlée par l'utilisateur.
Équivalent d'Andoff, mais liste ouverte.

## Stack
- Kotlin, natif Android, minSdk 24.
- Un seul `DeviceAdminReceiver` comme composant admin.

## Pré-requis (hors code)
- L'app devient Device Owner via :
  `adb shell dpm set-device-owner <pkg>/.AdminReceiver`
  sur un téléphone fraîchement réinitialisé (aucun compte Google présent).
- Un seul Device Owner par appareil (donc retirer Andoff d'abord).

## Config (en dur ou JSON)
- `targetBrowser` : `com.microsoft.emmx` (Edge) ou `com.android.chrome`
- `extensions` : liste de `"<id>;<updateUrl>"`
    - updateUrl Edge  : `https://edge.microsoft.com/extensionwebstorebase/v1/crx`
    - updateUrl Chrome: `https://clients2.google.com/service/update2/crx`
- `lockedApps` : packages à empêcher de désinstaller
- `hiddenApps` : packages à masquer / bloquer

## Algo — fonction applyPolicy() (idempotente)
1. Vérifier `dpm.isDeviceOwnerApp(pkg)`. Sinon : stop + message.
2. Extensions :
   `dpm.setApplicationRestrictions(admin, targetBrowser,
       Bundle{ "ExtensionInstallForcelist" = extensions[] })`
3. Apps non-désinstallables :
   pour chaque p dans lockedApps -> `dpm.setUninstallBlocked(admin, p, true)`
4. Apps bloquées :
   pour chaque p dans hiddenApps -> `dpm.setApplicationHidden(admin, p, true)`
5. Auto-protection :
   `dpm.setUninstallBlocked(admin, ownPackage, true)`

## Vérification
- Dans le navigateur : `edge://policy` (ou `chrome://policy`)
  -> `ExtensionInstallForcelist` statut OK, IDs présents.
- Les extensions apparaissent "gérées par l'organisation", sans bouton Supprimer.

## Contraintes à respecter
- Ne marche que si le navigateur honore la policy :
  Edge oui ; Chrome standard sur Android ne gère pas les extensions.
- Remettre l'ID Pluckeye dans `extensions` (cette app remplace Andoff).
- Sortie de secours :
  `adb shell dpm remove-active-admin <pkg>/.AdminReceiver`

## Hors scope minimal (phase 2)
- Délai anti-faiblesse : tout changement de liste n'entre en vigueur qu'après N heures.
- Blocage des autres VPN : restriction `DISALLOW_CONFIG_VPN`.
- Always-on VPN verrouillé : `setAlwaysOnVpnPackage(..., lockdown = true)`.
