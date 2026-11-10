# Squash Tap

A standalone, offline racket-sport scorekeeper for Wear OS. Current watch release: **v0.15.1**, Android version code **17**. No account is required; scores and saved history live on the watch.

[Download the watch app](https://squash-tap-andrew.endruuu.chatgpt.site/) · [GitHub source](https://github.com/andrew01ait/squash-tap) · [Install using your phone](INSTALLATION.md)

## Features

- Start with Squash, Tennis, Badminton, Padel or Table tennis. Results and statistics are separated by sport; eligible sports have singles/doubles filters.
- Use one shared Players catalog for opponents and partners. Small sport icons indicate saved results in any role; renames keep stable identities, and deleting/reopening results updates the icons.
- Keep two large scoring cards for singles or teams. Tennis/padel games advance automatically; select optional first-server choices for serving guidance. A bright independent serving badge and score-card outline stay visible with missing or long names; doubles use stable role labels. No first/second serve, let or stroke recording.
- Choose both old/new badminton and padel formats. Each match retains its own rules. See [Sports and scoring](RULES.md) for exact presets, service guidance and scope.

- Tap blue **Me** or red **Opponent** to score a rally. Squash PAR-11 requires a two-point lead; Undo corrects mistakes.
- Choose the supported match length for each sport, keep every set score, and optionally choose a saved opponent. New-match choices stay in a draft until Create match.
- Save completed matches automatically, or use Match options → Save retirement result to record an early win/loss. Current match → Cancel match → Discard match quits without saving scores or a statistics entry. Saved history and shared names are kept.
- Enter a completed set for the current match or add an independent past match using score wheels, large controls and saved names.
- Browse history with combined opponent/date filters: **10 matches per watch page**, newest first. Returning from a result preserves the page, filters and scroll position.
- See match wins/losses, game/set totals and known points for the selected sport/mode. The phone view also provides per-opponent balances and recent W/L history.
- View results, edit names and import/export CSV through an optional encrypted, ten-minute QR session in a phone browser. Phone results use 25 matches per page.
- Check for watch updates and export diagnostics from Settings. An explicit active match controls the ongoing session, ambient scoreboard and screen retention.

## Where to find things

The launcher opens the five-sport selector. Every sport has a distinct icon and accent colour, also shown in menus and on the scoreboard. There is one current match across the app. Other sports are locked while an unfinished match exists, including paused or prepared matches; finish or cancel it first. Shared Players and Settings remain accessible and return to their actual caller.

| Menu | Purpose |
| --- | --- |
| Current match | Start/Pause/Resume, Enter set score, Match details and Match options. |
| Results | Saved history, Filters, Add past match and Options for phone viewing, CSV and bulk deletion. |
| Statistics | Overall results across all saved completed matches, independent of history filters. |
| Players | Add, rename or remove names; optionally edit the catalog on the phone. |
| Settings | Version, updates, display/notification options and diagnostics. |

Match actions are blue, saved people purple, Settings grey, discard/deletion red and Back/Cancel outlined. Menu sections separate sport actions from app-wide controls. Mode, scoring rules, deuce, deciding set, match length, history mode and screen policy open explicit selection lists with the current choice marked. Buttons, hardware Back and the native swipe-dismiss callback share the same parent destination. Full navigation is documented in [FLOW.md](FLOW.md).

## Timing and manual entry

Timing starts on the first accepted live point. Start match enables availability before a point without inventing a start time. If no start exists when a valid manual set is saved, that save records the current watch date/time/zone as the start. Existing starts are preserved.

A live winning point or confirmed retirement records the finish. A manually entered match finish is unknown; independent historical matches remain date-only. Duration includes pauses and set breaks when both times are known. Same-day details display the date once and short Start/End times; overnight matches also show the finish date. Legacy unknown times stay unknown.

Choose **Current match → Enter set score** to change the current match. Choose **Results → Add past match** to append separate history without changing current points, opponent, timing or session. Entered totals do not invent rally order, and Undo of an entered set asks before removing the whole set.

## Players, history and statistics

Names are optional. Adding a name on the phone never assigns it to the last/current match. Match reassignment needs a separate explicit opt-in and watch review. Renames follow stable opponent IDs. Removing a name archives it from future selection while retaining its identity in old results and filters.

Deleting a result requires confirmation and never reuses its daily match number. Deleting all history keeps the current scoreboard. Undo/re-completion preserves the original reference and cannot revive a deliberately deleted current result. Retired players lose the match; actual sets/points alone enter their corresponding totals.

## Phone transfers and privacy

On the watch, open **Results → Options → View results on phone → Show phone QR**. Scan it with the phone camera. Both devices need internet for this optional transfer; playing and recording scores remain offline. A companion phone app is unnecessary after initial APK installation.

Each transfer has independent random secrets. The QR carries the decryption key in its URL fragment; the relay stores encrypted payloads and hashed access capabilities. Anyone holding the complete active link can open that transfer, so keep it private. There is no public history listing or permanent account-based cloud history.

Access expires after ten minutes. Ending/applying requests deletion immediately; unused expired ciphertext is physically cleaned during later API traffic, not at a guaranteed scheduled erasure time. Downloaded CSV files remain on the phone until their owner removes them. CSV schemas 1/2 remain readable as squash; schema 3 preserves sport, rules, team identities, seven-game histories and known point/tie-break details. Older watches must update before importing schema 3. CSV is a completed-results interchange, not a complete backup of active rallies, Undo or unused names. See [ARCHITECTURE.md](ARCHITECTURE.md) for limits and the data flow.

## Keeping a match available

The first point or Start/Resume enables an explicit match session. Pause, completion, replacement or task dismissal stops it. Keep screen on applies only to the visible scoreboard of an active match; idle drafts, paused/completed matches and panels allow normal display sleep. Ordinary activity pause/ambient transitions preserve an explicitly running match.

Updates are requested manually in Settings. There is no background update polling, health/sensor tracking or app-held CPU wake lock. Diagnostics include whole-watch battery snapshots, not app-attributed energy measurements. Physical battery and gesture acceptance remain separate from automated checks.

## Documentation

| Guide | Audience and contents |
| --- | --- |
| [INSTALLATION.md](INSTALLATION.md) | Phone-only installation, German OnePlus labels, direct updates and diagnostics. |
| [RULES.md](RULES.md) | Sport presets, old/new rules, optional serving and scorekeeper limits. |
| [FLOW.md](FLOW.md) | Current menu routes, staged choices, returns and user actions. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Native components, data formats/migrations, power policy, relay and update verification. |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Maintainer build, signing, retained APKs, website publication and release procedure. |
| [TESTING.md](TESTING.md) | Reproducible checks, recorded release evidence and physical-device acceptance. |
| [VERSIONING.md](VERSIONING.md) | Watch versus website versions, compatibility, rollback and documentation-only changes. |
| [GITHUB.md](GITHUB.md) | Source layout, public/internal differences, contribution/sync procedure and fork prerequisites. |
| [AGENTS.md](AGENTS.md) | Maintenance constraints for future coding work. |
| [CHANGELOG.md](CHANGELOG.md) | Release and documentation history. |

The current v0.15.1 release suite passes **299 core + 135 Android UI + 43 web/relay checks = 477**. The historical v0.13 release suite was **265 core + 100 Android UI + 35 web/relay checks = 400**. This is release evidence, not a claim that every documentation update reruns or physically validates the watch. See [TESTING.md](TESTING.md) for current verification.

Future user-facing JSON exports use the local export date and application name: `YYYYMMDD_ApplicationName_Description.json`, for example `20261109_SquashTap_results.json`. Current match interchange is CSV; this naming rule does not add a JSON export feature.

The landing page offers keyboard-accessible previews of all five scoreboards, supported formats, native menu/name examples, installation/update help and transfer privacy. Preview images use synthetic data, never real player exports.
