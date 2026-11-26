# Next steps / gaps

Short notes on what's not done yet and the logical next moves.

## Known gaps
- **Imported blocklist removal is immediate** — not gated by the reflection delay. For a
  self-control porn blocker this is a hole; removal should defer like any other loosening.
- **List quota counts web/feed time only** — apps in a list obey the list *window* and their
  own per-app quota, not the shared list budget (summing UsageStats + browser time needs a
  context-aware fold).
- **Cosmetic/element-import not done** — hiding parts of big sites (YouTube/IG/Twitter) from a
  GitHub filter list needs dynamic per-domain CSS injection; GeckoView only loads static
  bundled extensions today, so this is a separate, harder feature.
- **Dead code**: `ServicesScreen` / `ServiceDetailScreen` are unused since the Apps tab was
  removed. Delete once the lists flow is settled.

## Logical next steps
1. Defer imported-list removal (and ideally list delete) through the reflection delay.
2. Periodic blocklist refresh (re-fetch the source URL on a schedule).
3. Make list quota app-inclusive (sum browser + UsageStats time in a context-aware helper).
4. Tab previews for non-active tabs (snapshot on switch-away, not just the current tab).
5. Firewall `lockdown = true` once the filter is proven (currently false in `FirewallPolicy`).
