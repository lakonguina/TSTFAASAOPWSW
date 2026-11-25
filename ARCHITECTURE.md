# Architecture

How the app works, short version.

## The one idea: one-way commitment

The app stores your **intent** (rules + pending changes), not the machine state. A single
idempotent function, `PolicyManager.reconcile`, replays on every open, alarm and reboot:
it matures due pending changes (`PendingQueue`), then pushes the intent to the device.

- **Tighten** (block, add a rule, narrow a window, raise the delay) → immediate.
- **Loosen** (unblock, delete a rule, lower the delay) → deferred by the current reflection
  delay; locked meanwhile, with a countdown.

## Services

A **service** groups one product's app **packages** and website **domain** (e.g. Twitter/X =
`com.twitter.android` + `x.com`). Services are editable data (`ServiceDef`), seeded once from
`Config.catalog`: create, rename, change package/domain, delete (unless the app is installed).
A service shows even when its app isn't installed, so its web rules can be set in advance and
the app pre-blocked against future install. Logos are the site's favicon.

## Two screens

- **Apps** — the services list. `+` blocks a URL (filed under the matching service) or creates
  a service.
- **General** — Device Owner status + the reflection delay.

A service page has an **Application** block and a **Web** block (whole-site block, feed/navbar
hide rules, and user-added URL rules). Each rule has a block switch, an optional daily
**allowed-hours** window (enforced), and a daily **minutes quota** (stored and shown, not yet
enforced). Built-in rules are fixed; user rules are editable and deletable.

## Three enforcement surfaces, one brain

| Surface | Role | Scope |
|---|---|---|
| **Device Owner** | hide/freeze apps, block installs, lock exits | whole phone |
| **DNS firewall** (local VPN, always on) | block a **domain** | all apps |
| **Browser + extensions** | block a **path**, hide page parts | our browser |

Allowed-hours windows fold into effective blocked sets (`effectiveBlockedApps/Web`,
`effectiveEnabledElements`) that these surfaces already enforce; `reconcile` reschedules an
alarm at the next window edge.

## Updates & escape

Auto-update from GitHub Releases: push tag `vN`, CI builds/signs/publishes, the app installs
it silently (Device Owner). The dev phone runs a **debug** build (different signing key), so
push to it with `adb install -r`. Escape hatch:
`adb shell dpm remove-active-admin <pkg>/.AdminReceiver`.

## To do

Enforce the daily minutes quota: measure real usage (browser timer for web,
`UsageStatsManager` for apps, usage-access granted once, daily reset), show time left, block
when exhausted.
