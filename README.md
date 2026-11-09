# Squash Tap

A standalone, offline squash scorekeeper for Wear OS. Current watch release: **v0.13.0**, Android version code **14**. No account is required; scores and saved history live on the watch.

[Download the watch app](https://squash-tap-andrew.endruuu.chatgpt.site/) · [GitHub source](https://github.com/andrew01ait/squash-tap) · [Install using your phone](INSTALLATION.md)

## Features

- Tap blue **Me** or red **Opponent** to score a rally. PAR-11 scoring requires a two-point lead; Undo corrects mistakes.
- Play best of three or five, keep every set score, and optionally choose a saved opponent. New-match choices stay in a draft until Create match.
- Save completed matches automatically, or end a match early with a confirmed retirement. Results keep actual scores rather than fabricated winning sets.
- Enter a completed set for the current match or add an independent past match using score wheels, large controls and saved names.
- Browse history with combined opponent/date filters: **10 matches per watch page**, newest first. Returning from a result preserves the page, filters and scroll position.
- See overall match wins/losses, sets and points. The phone view also provides per-opponent balances and recent W/L history.
- View results, edit names and import/export CSV through an optional encrypted, ten-minute QR session in a phone browser. Phone results use 25 matches per page.
- Check for watch updates and export diagnostics from Settings. An explicit active match controls the ongoing session, ambient scoreboard and screen retention.

## Where to find things

| Menu | Purpose |
| --- | --- |
| Current match | Start/Pause/Resume, Enter set score, Match details and Match options. |
| Results | Saved history, Filters, Add past match and Options for phone viewing, CSV and bulk deletion. |
| Statistics | Overall results across all saved completed matches, independent of history filters. |
| Opponents | Add, rename or remove names; optionally edit the catalog on the phone. |
| Settings | Version, updates, display/notification options and diagnostics. |

Match controls are blue, saved people purple, Settings grey, deletion red and Back/Cancel outlined. Buttons, hardware Back and the native swipe-dismiss callback share the same parent destination. Full navigation is documented in [FLOW.md](FLOW.md).

## Timing and manual entry

Timing starts on the first accepted live point. Start match enables availability before a point without inventing a start time. If no start exists when a valid manual set is saved, that save records the current watch date/time/zone as the start. Existing starts are preserved.

A live winning point or confirmed retirement records the finish. A manually entered match finish is unknown; independent historical matches remain date-only. Duration includes pauses and set breaks when both times are known. Same-day details display the date once and short Start/End times; overnight matches also show the finish date. Legacy unknown times stay unknown.

Choose **Current match → Enter set score** to change the current match. Choose **Results → Add past match** to append separate history without changing current points, opponent, timing or session. Entered totals do not invent rally order, and Undo of an entered set asks before removing the whole set.

## Names, history and statistics

Names are optional. Adding a name on the phone never assigns it to the last/current match. Match reassignment needs a separate explicit opt-in and watch review. Renames follow stable opponent IDs. Removing a name archives it from future selection while retaining its identity in old results and filters.

Deleting a result requires confirmation and never reuses its daily match number. Deleting all history keeps the current scoreboard. Undo/re-completion preserves the original reference and cannot revive a deliberately deleted current result. Retired players lose the match; actual sets/points alone enter their corresponding totals.

## Phone transfers and privacy

On the watch, open **Results → Options → View results on phone → Show phone QR**. Scan it with the phone camera. Both devices need internet for this optional transfer; playing and recording scores remain offline. A companion phone app is unnecessary after initial APK installation.

Each transfer has independent random secrets. The QR carries the decryption key in its URL fragment; the relay stores encrypted payloads and hashed access capabilities. Anyone holding the complete active link can open that transfer, so keep it private. There is no public history listing or permanent account-based cloud history.

Access expires after ten minutes. Ending/applying requests deletion immediately; unused expired ciphertext is physically cleaned during later API traffic, not at a guaranteed scheduled erasure time. Downloaded CSV files remain on the phone until their owner removes them. CSV is a completed-results interchange, not a complete backup of active rallies, Undo or unused names. See [ARCHITECTURE.md](ARCHITECTURE.md) for limits and the data flow.

## Keeping a match available

The first point or Start/Resume enables an explicit match session. Pause, completion, replacement or task dismissal stops it. Keep screen on applies only to the visible scoreboard of an active match; idle drafts, paused/completed matches and panels allow normal display sleep. Ordinary activity pause/ambient transitions preserve an explicitly running match.

Updates are requested manually in Settings. There is no background update polling, health/sensor tracking or app-held CPU wake lock. Diagnostics include whole-watch battery snapshots, not app-attributed energy measurements. Physical battery and gesture acceptance remain separate from automated checks.

## Documentation

| Guide | Audience and contents |
| --- | --- |
| [INSTALLATION.md](INSTALLATION.md) | Phone-only installation, German OnePlus labels, direct updates and diagnostics. |
| [FLOW.md](FLOW.md) | Current menu routes, staged choices, returns and user actions. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Native components, data formats/migrations, power policy, relay and update verification. |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Maintainer build, signing, retained APKs, website publication and release procedure. |
| [TESTING.md](TESTING.md) | Reproducible checks, recorded release evidence and physical-device acceptance. |
| [VERSIONING.md](VERSIONING.md) | Watch versus website versions, compatibility, rollback and documentation-only changes. |
| [GITHUB.md](GITHUB.md) | Source layout, public/internal differences, contribution/sync procedure and fork prerequisites. |
| [AGENTS.md](AGENTS.md) | Maintenance constraints for future coding work. |
| [CHANGELOG.md](CHANGELOG.md) | Release and documentation history. |

The recorded v0.13 release suite was **265 core + 100 Android UI + 35 web/relay checks = 400**. This is release evidence, not a claim that every documentation update reruns or physically validates the watch. See [TESTING.md](TESTING.md) for current verification.

Future user-facing JSON exports use the local export date and application name: `YYYYMMDD_ApplicationName_Description.json`, for example `20261109_SquashTap_results.json`. Current match interchange is CSV; this naming rule does not add a JSON export feature.
