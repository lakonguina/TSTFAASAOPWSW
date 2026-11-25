# TSTFAASAOPWSW

*The Software To Fight Algorithms As Strong As Optimus Prime Without Sheer Willpower.*

What if I told you that tomorrow you had to fight Optimus Prime bare-handed? You'd say
that's about the dumbest idea possible — you're going to get crushed. That's exactly what
you do every time you pick up your phone. You're fighting algorithms (Twitter, Instagram,
notifications…) as strong as a 30-meter robot. You just underestimate them, because they
have no physical body — they're purely digital.

So don't fight them every day. Commit the phone *for* you, once.

## What it does

A personal Android self-discipline tool that runs as **Device Owner** on your own phone.

- **Block apps and sites**, and hide distraction surfaces (feeds, navbars).
- **Add your own URL rules** (e.g. `x.com/settings`), filed under the right service.
- **Allowed-hours** per rule: reachable only inside a daily window, blocked otherwise.
- **One-way commitment**: tightening is instant; loosening only applies after a reflection
  delay (and stays locked, with a countdown, until then).
- Embedded browser enforced as default; a phone-wide DNS filter is always on.

## Build & install

JDK 17 (Android Studio's JBR works). `./gradlew assembleDebug`, then
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Device Owner, on a freshly reset phone with no Google account:
`adb shell dpm set-device-owner <pkg>/.AdminReceiver`. Escape hatch:
`adb shell dpm remove-active-admin <pkg>/.AdminReceiver`.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how it works.
