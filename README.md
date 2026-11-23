# ForceList

Outil **personnel** d'auto-discipline pour mon propre téléphone Android.

Il verrouille quelques-uns de mes réglages — force mes extensions de filtrage dans
Edge, garde mes outils de contrôle non-désinstallables, met mes apps distrayantes en
pause — pour que je ne puisse pas les défaire sur un coup de tête. Même principe que
Family Link, Temps d'écran ou Pluckeye/Andoff : un engagement envers moi-même.

C'est mon appareil, que je configure moi-même en clair via adb. Il reste une sortie de
secours (voir plus bas).

Technique : API officielles Android Enterprise / `DevicePolicyManager`, celles qu'emploient
tous les outils de gestion d'appareil. Kotlin natif, minSdk 24, aucune dépendance externe.

## Ce que je configure (dans l'app, plus dans le code)

Tout se pilote depuis l'écran de l'app, et c'est persistant :

- **Délai de réflexion** — sélecteur en haut. Règle d'auto-engagement façon Pluckeye :
  **serrer** (bloquer une app, ajouter une extension, **augmenter** le délai) prend effet
  **immédiatement** ; **desserrer** (débloquer, retirer une extension, **baisser** le délai)
  n'entre en vigueur **qu'après le délai courant**. L'attente est visible (« déblocage dans
  ~N min ») et se déclenche seule (alarme + au redémarrage).
- **Applications** — liste de mes apps, un interrupteur Bloqué/Autorisé par app. Bloqué =
  masquée / mise en pause (`setApplicationHidden`).
- **Extensions forcées (Edge)** — j'ajoute un ID d'extension ; il est forcé via
  `ExtensionInstallForcelist`. Le retrait suit le délai.

Seules constantes encore dans [`Config.kt`](app/src/main/java/com/tstfaasaopwsw/Config.kt) :
le navigateur cible, l'URL de mise à jour Edge, et les choix du sélecteur de délai.
L'état vécu est dans `SharedPreferences` ([`Store`](app/src/main/java/com/tstfaasaopwsw/Store.kt)).

## Construire

Le wrapper Gradle est inclus (`./gradlew`), inutile d'installer Gradle.
Il faut un **JDK 17**.

- **Android Studio** : ouvrir le dossier → Run. Studio fournit son propre JDK et
  télécharge au besoin la plateforme SDK (`android-34`). Le plus simple.
- **Ligne de commande** : avec un JDK 17 pointé par `JAVA_HOME` (celui d'Android
  Studio convient, souvent `~/android-studio/jbr` ou `/opt/android-studio/jbr`) :

```bash
export JAVA_HOME=/chemin/vers/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug   # produit app/build/outputs/apk/debug/app-debug.apk
```

> Compilé et vérifié avec JDK 17 + AGP 8.5.2 ; la plateforme `android-34` est
> téléchargée automatiquement au premier build si absente.

## Tester (sans sacrifier mon vrai téléphone)

Un Device Owner ne s'installe que sur un appareil **sans compte**. Pour éprouver la
logique d'abord, le mieux est un **émulateur jetable** — je le crée, je casse, je
recrée sans conséquence.

1. Créer un AVD avec une image système **sans Google Play** (sinon un compte est
   présent et `set-device-owner` échoue), via Android Studio (Device Manager) ou en
   ligne de commande, puis le démarrer.
2. Installer et désigner comme Device Owner :

   ```bash
   adb install -t app/build/outputs/apk/debug/app-debug.apk   # -t : app testOnly
   adb shell dpm set-device-owner com.tstfaasaopwsw/.AdminReceiver
   ```

3. Vérifier chaque effet :

   ```bash
   adb shell dpm list-owners                 # doit lister mon app
   adb shell pm list packages | grep instagram   # une app masquée disparaît de la liste
   adb shell pm uninstall com.tstfaasaopwsw  # -> Failure (désinstallation bloquée)
   ```

   - Les apps de `hiddenApps` disparaissent du lanceur.
   - Dans l'app : l'écran récapitule chaque étape en ✓ / ✗.
   - Extensions : seulement si Edge est installé sur l'AVD → `edge://policy`.

4. Pour recommencer à zéro : `adb shell dpm remove-active-admin com.tstfaasaopwsw/.AdminReceiver`,
   ou simplement supprimer l'AVD.

Astuce : `adb shell pm list packages` me donne les noms de packages exacts à mettre
dans [`Config.kt`](app/src/main/java/com/tstfaasaopwsw/Config.kt).

## Mise en place sur mon vrai téléphone (réinitialisé, sans compte Google)

```bash
./gradlew assembleRelease        # APK signé avec MA clé stable
adb install -t app/build/outputs/apk/release/app-release.apk
adb shell dpm set-device-owner com.tstfaasaopwsw/.AdminReceiver
```

> ⚠️ Toujours provisionner le vrai téléphone avec l'**APK release** (clé stable),
> jamais le debug — sinon aucune mise à jour future ne sera acceptée (voir plus bas).

Un seul Device Owner par appareil — si je viens d'Andoff, je le retire d'abord et je
remets l'ID Pluckeye dans `extensions`.

Dès l'activation, `AdminReceiver.onEnabled` applique la politique. Je peux aussi ouvrir
l'app et toucher **Appliquer ma politique** : l'opération est idempotente, rejouable à
volonté.

## Vérifier

- Dans Edge : `edge://policy` → `ExtensionInstallForcelist` au statut OK, mes IDs présents.
- Mes extensions apparaissent « gérées par l'organisation », sans bouton Supprimer.
- L'écran de l'app récapitule chaque étape (✓ / ✗).

## Sortie de secours

```bash
adb shell dpm remove-active-admin com.tstfaasaopwsw/.AdminReceiver
```

Lève la politique sans réinitialiser (horloge remasquée rendue, désinstallation
débloquée). Cette commande ne fonctionne que parce que l'app est marquée
**`android:testOnly="true"`** — sinon Android refuse de retirer un Device Owner et il
faut un factory reset. La friction voulue reste là : il faut un PC avec adb, donc pas
de retour en arrière impulsif depuis le téléphone.

## Limites

- Ne marche que si le navigateur honore la policy : **Edge oui**, Chrome standard sur
  Android ne gère pas les extensions.
- `set-device-owner` exige un appareil fraîchement réinitialisé, sans compte ajouté.
- App `testOnly` : installer avec `adb install -t` (Android Studio le gère seul). En
  contrepartie, pas de distribution via le Play Store — sans objet pour un outil perso.

## Signature & mises à jour

L'app est signée avec **une clé stable** (`release.keystore`), pas la clé debug
jetable. C'est ce qui rend l'outil **évolutif sans réinstaller ni réinitialiser** :

- Faire évoluer l'app plus tard (nouvelles listes, auto-update, délai de réflexion…) =
  `./gradlew assembleRelease` puis `adb install -r -t …app-release.apk`. Le Device
  Owner et les données **survivent** à la mise à jour, **tant que la signature est la
  même**. Penser à **incrémenter `versionCode`** à chaque version.
- Android n'accepte une mise à jour que si le nouvel APK a la **même signature** que
  l'installé. D'où la clé stable fixée une fois pour toutes.

🔑 **À sauvegarder précieusement, hors de ce dépôt :** `release.keystore` **et**
`keystore.properties` (ils contiennent la clé + le mot de passe, et sont gitignorés).
**Les perdre = impossible de mettre à jour l'app installée → factory reset obligatoire.**
Les secrets locaux suivent le modèle [`keystore.properties.example`](keystore.properties.example) ;
pour un build CI (GitHub Actions), fournir le keystore en base64 + ces valeurs via des secrets.

## Phase 2 (hors scope)

- Délai de réflexion : un changement de mes listes ne s'applique qu'après N heures.
- `DISALLOW_CONFIG_VPN`, always-on VPN verrouillé (`setAlwaysOnVpnPackage(..., lockdown = true)`).
