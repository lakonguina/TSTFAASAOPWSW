# TSTFAASAOPWSW

**The Software to Fight Algorithms as Strong as Optimus Prime Without Sheer Willpower.**

Feeds and recommendation engines are built to beat your willpower — fighting them
head-on is a losing match against Optimus Prime. This is a personal Android
self-discipline tool that commits the device *for* you, so you don't have to win that
fight every day. Same spirit as Family Link, Screen Time or Pluckeye.

It runs as **Device Owner** (Android Enterprise / `DevicePolicyManager`) on my own
phone, which I provision myself over adb. Native Kotlin, minSdk 24.

## What it does

Everything is toggled from the app and persists.

- **Reflection delay** — tightening (block an app, raise the delay) is immediate;
  loosening (unblock, lower the delay) only applies **after the current delay**. The
  wait is visible and fires on its own (alarm + reboot). Friction by design.
- **Apps** — a Blocked/Allowed switch per app. Blocked = hidden + suspended, and kept
  from coming back via reinstall.
- **Web** — block a domain or a full URL (path-level in the built-in browser,
  domain-level system-wide through a local DNS firewall).
- **Hide elements** — kill the distraction surfaces directly: the X home feed, the
  YouTube home feed… Each is a toggle; hiding is done by a bundled content script using
  stable, language-independent selectors (`data-testid`), scoped to the right page.
- **Browser** — an embedded GeckoView browser, enforced as the default, so web rules
  can't be dodged. Google as the search engine.
- **Auto-update** — pulls the latest signed APK from GitHub Releases (Device Owner
  installs it silently). The Device Owner and data survive updates.

## Build

Gradle wrapper included; needs **JDK 17**. Android Studio is simplest (Run). CLI:

```bash
export JAVA_HOME=/path/to/jdk-17   # Android Studio's JBR works
./gradlew assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk
```

compileSdk 35 / AGP 8.6; `android-35` is downloaded on first build if missing.

## Install as Device Owner

`set-device-owner` only works on a **freshly reset phone with zero accounts**. Provision
with the **release** APK (stable key), never debug — otherwise future updates are
rejected.

```bash
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
adb shell dpm set-device-owner com.tstfaasaopwsw/.AdminReceiver
```

## Keystore (keep it safe)

The app is signed with a **stable key** (`release.keystore` + `keystore.properties`,
both gitignored). Android only accepts an update signed with the same key. **Lose the
key = can't update the installed app → factory reset.** For CI, pass the keystore as a
base64 secret (see `.github/workflows/release.yml`).

## Escape hatch

There is no one-tap removal — that's the point. Taking off the Device Owner needs a PC
with adb (and the device at zero accounts), so there's no impulsive undo from the phone.
