# Next steps / gaps

Short notes on what's not done yet and the logical next moves.

## Done in this pass
- **Deferred imported-list removal** — removing an imported blocklist now goes through the
  reflection delay (pending kind `LR`); the list keeps blocking until it matures. No delay =
  immediate, like every other loosening. (`RuleLists.removeImportedList`, `Store.applyDue`.)
- **Periodic blocklist refresh** — each imported list re-fetches its source URL at most once a
  day, off the main thread, on every alarm/boot tick. A grown upstream list tightens at once; a
  shrunk one is the source's call, applied directly. (`ImportedLists.refreshInBackground`.)
- **App-inclusive list quota** — a list's shared quota now counts app foreground time too:
  `PolicyManager` folds each quota-list app's system-measured seconds into the same usage
  counters the browser uses, so `listUsedSeconds` sums web + feed + app uniformly, and a spent
  shared quota closes the list's apps as well. (`RuleLists`, `Usage.setUsageSeconds`, `Schedules`.)
- **Tab previews on switch-away** — every tab is snapshotted into its thumbnail just before it
  stops being the live one (new tab, open-in-new-tab, opening the grid), so the Tabs grid shows
  a fresh preview for all tabs, not only the one open when the grid appeared. (`BrowserScreen`.)
- **Firewall lockdown** — kept `false` by default (a filter bug must not cut the network), but
  added a **Settings → Firewall → Lockdown mode** toggle so it can be switched on once the
  filter is proven. (`Store.firewallLockdown`, `FirewallPolicy.apply`, `SettingsScreen`.)
- **Many more hideable surfaces** — the element catalog grew from 3 to 15 built-in rules across
  X, YouTube, Reddit, Instagram, TikTok and Facebook (YouTube thumbnails/Shorts/comments/
  up-next, Reddit & Instagram & TikTok home feeds, Explore/Reels pages, Facebook feed &
  Marketplace…). Each is a tiny bundled extension in `assets/rules/<id>`: sitewide ones ship a
  `hide.css`, path-scoped ones a `hide.js`. Add a rule = add a `Config.elementCatalog` entry +
  its bundle.

## Known gaps
- **New cosmetic selectors need on-device tuning** — the CSS/JS use stable hooks (custom
  elements, `data-testid`, `role`, real `<article>` tags) but were written without a device.
  Instagram/TikTok especially are heavily obfuscated; verify each toggle on the phone and adjust
  the selector in its `hide.*` file. The X/YouTube ones lean on documented, long-stable hooks.
- **Cosmetic rules are bundled, not remote** — adding a *new* site rule still needs an app
  release. A generic `<all_urls>` extension that fetches a `cosmetics.json` from GitHub at page
  load (intra-extension messaging, no app↔extension bridge) would let new rules ship without a
  rebuild. Deferred on purpose; the static catalog is simpler and covers the common sites.
- **Manual list delete is still immediate** — only *imported* list removal defers. A hand-built
  list's delete should defer too (it's a loosening).
- ~~Dead code `ServicesScreen` / `ServiceDetailScreen`~~ — **revived**: the **Apps** tab is
  back alongside Lists (Web · Apps · Lists · General). Rules are grouped by service with logos;
  tapping one opens all its rules (app block, whole-site, every hideable surface, URL rules).

## Logical next steps
1. Tune the new cosmetic selectors on-device, per toggle (one `hide.*` file each).
2. Surface "last updated" + a manual **Refresh** button in the imported-list detail screen.
3. Defer manual list delete through the reflection delay (mirror `removeImportedList`).
4. Remote cosmetic rules: a generic fetch-driven extension so new site rules ship via GitHub,
   not an app release — only if the bundled catalog proves too rigid.
5. Once the filter is proven, default Lockdown on (or drop the toggle and hard-enable it).

## Longer-term: desktop / multiplatform (KMP)

Goal: run on desktop (Windows/macOS/Linux) alongside Android, sharing as much code as
possible, keeping the same features where the OS allows.

**Approach — Kotlin Multiplatform (KMP).** One shared module holds everything platform-neutral;
OS-specific capabilities sit behind interfaces, each with a thin per-OS adapter
(ports-and-adapters). Write the core once, plug the OS parts per platform.

**Repo — monorepo, not polyrepo.** A single repo with Gradle modules (`:shared`, `:android`,
`:desktop`, later `:ios`). KMP tooling assumes one repo; this gives atomic changes across core
and platforms, one build, one CI. Polyrepo would force publishing the shared module as a
versioned artifact and consuming it — version churn and slow iteration for no gain at this size.

**What's shared vs per-platform:**

| Component | Shared core (KMP) | Per-platform adapter |
|---|---|---|
| Logic + store (rules, pending queue, schedules, quotas, blocklist parse, URL match) | ~100% | `KeyValueStore`: SharedPreferences / desktop file |
| UI (Compose) | ~70–80% via Compose Multiplatform | the liquid-glass effect (Android AGSL) → re-skin |
| Browser + CSS rules | the rules themselves (CSS = CSS, Chromium eats the same selectors) + the rule model | `BrowserEngine`: GeckoView / CEF-Chromium (inject + intercept nav) |
| DNS firewall | the decision (`isDomainImportBlocked` / `suffixMatch`) | `NetworkFilter`: VpnService / local DNS resolver or hosts / OS firewall |
| App blocking (Device Owner) | just the block-intent state | `AppController`: DPM / Windows firewall+kill, macOS launchd, Linux cgroup |
| App time (UsageStats) | the quota logic | `AppUsageProbe`: UsageStatsManager / foreground-window per OS |
| Updater | the GitHub check | silent install per OS |

**The hard part.** Everything mutualises well *except app blocking + anti-tamper*: there is no
clean cross-OS equivalent of Device Owner, and on desktop the user is admin (can kill the
process, edit hosts, uninstall), so the commitment guarantee is fundamentally weaker there. Web
features (block site, hide feed, schedules, quotas, imported blocklists) port cleanly behind
`BrowserEngine` + `NetworkFilter`; the CSS rules are 100% portable as-is.

**First concrete step** (cheap, worth doing even without desktop): extract a `KeyValueStore`
interface — the test `FakeSharedPreferences` already proves the seam — with an Android
SharedPreferences backend and a desktop file backend. The domain logic then stops depending on
Android at all.
