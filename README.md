# Keep Score

A standalone, offline racket-sport scorekeeper for Wear OS. Current watch release: **v0.16.1**, Android version code **19**. No account is required; scores and saved history live on the watch.

[Download the watch app](https://squash-tap-andrew.endruuu.chatgpt.site/) · [GitHub source](https://github.com/andrew01ait/squash-tap) · [Install using your phone](INSTALLATION.md)

## Features

- Start with Squash, Tennis, Badminton, Padel, Table tennis or Pickleball. Results and statistics are separated by sport; eligible sports have singles/doubles filters.
- Use one shared Players catalog for opponents and partners. Small sport icons indicate saved results in any role; renames keep stable identities, and deleting/reopening results updates the icons.
- Keep two large scoring cards for singles or teams. Tennis/padel games advance automatically; select optional first-server choices for serving guidance. A bright independent serving badge and score-card outline stay visible with missing or long names; doubles use stable role labels. No first/second serve, let or stroke recording.
- Choose both old/new badminton, padel and pickleball formats. Each match retains its own rules. See [Sports and scoring](RULES.md) for exact presets, service guidance and scope.

- Tap the left **Me** or right **Opponent** card to score a rally. Squash PAR-11 requires a two-point lead; Undo corrects mistakes.
- Choose the supported match length for each sport, keep every set score, and optionally choose a saved opponent. New-match choices stay in a draft until Create match.
- Save completed matches automatically, or use Match options → Save retirement result to record an early win/loss. Current match → Cancel match → Discard match quits without saving scores or a statistics entry. Saved history and shared names are kept.
- Enter a completed set for the current match or add an independent past match using score wheels, large controls and saved names.
- Browse history with combined opponent/date filters: **10 matches per watch page**, newest first. Returning from a result preserves the page, filters and scroll position.
- See match wins/losses, game/set totals and known points for the selected sport/mode. The phone view also provides per-opponent balances and recent W/L history.
- View results, edit names and import/export CSV through an optional encrypted, ten-minute QR session in a phone browser. Phone results use 25 matches per page.
- Check for watch updates and export diagnostics from Settings. An explicit active match controls the ongoing session, ambient scoreboard and screen retention.

- Quickly rematch the last completed current match with the same rules and available players, retaining its result. Save up to 24 named favourite formats.
- Choose Classic, Neon Lime or high-contrast Sunlight scoreboard colours from a preview list. Brightness follows watch system settings; the app has no light-sensor loop or forced brightness.
- Choose point/milestone/off vibrations and optional non-blocking change-ends/interval prompts. Add a read-only match tile or score complication from the watch’s system pickers.
- Download a complete local backup, share result-card PNGs or explicitly start an encrypted read-only live audience scoreboard when internet is available.

## Where to find things

The launcher opens the six-sport selector. Every sport has a distinct icon and accent colour, also shown in menus and on the scoreboard. There is one current match across the app. Other sports are locked while an unfinished match exists, including paused or prepared matches; finish or cancel it first. Shared Players and Settings remain accessible and return to their actual caller.

| Menu | Purpose |
| --- | --- |
| Current match | Start/Pause/Resume, Enter set score, Match details, Live scoreboard and Match options. |
| Favourite formats | Reuse sport-specific scoring presets saved from New match. |
| Results | Saved history, Filters, Add past match and Options for phone viewing, CSV and bulk deletion. |
| Statistics | Overall results across all saved completed matches, independent of history filters. |
| Players | Add, rename or remove names; optionally edit the catalog on the phone. |
| Settings | Full backup/restore, colour schemes, system brightness help, vibrations, reminders, tile/complication help, updates and diagnostics. |

Match actions are blue, saved people purple, Settings grey, discard/deletion red and Back/Cancel outlined. Menu sections separate sport actions from app-wide controls. Mode, scoring rules, deuce, deciding set, match length, history mode and screen policy open explicit selection lists with the current choice marked. Buttons, hardware Back and the native swipe-dismiss callback share the same parent destination. Full navigation is documented in [FLOW.md](FLOW.md).

## Timing and manual entry

Timing starts on the first accepted live point. Start match enables availability before a point without inventing a start time. If no start exists when a valid manual set is saved, that save records the current watch date/time/zone as the start. Existing starts are preserved.

A live winning point or confirmed retirement records the finish. A manually entered match finish is unknown; independent historical matches remain date-only. Duration includes pauses and set breaks when both times are known. Same-day details display the date once and short Start/End times; overnight matches also show the finish date. Legacy unknown times stay unknown.

Choose **Current match → Enter set score** to change the current match. Choose **Results → Add past match** to append separate history without changing current points, opponent, timing or session. Entered totals do not invent rally order, and Undo of an entered set asks before removing the whole set.

## Players, history and statistics

Names are optional. Players → Your name sets your own display name (blank means Me), independently of the opponent catalog. Your name appears on the watch, private phone view, result-card image and live scoreboard. Adding a name on the phone never assigns it to the last/current match. Match reassignment needs a separate explicit opt-in and watch review. Renames follow stable opponent IDs. Removing a name archives it from future selection while retaining its identity in old results and filters.

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

The current v0.16.1 release suite passes **325 core + 185 Android UI + 58 web/relay checks = 568**. The historical v0.13 release suite was **265 core + 100 Android UI + 35 web/relay checks = 400**. This is release evidence, not a claim that every documentation update reruns or physically validates the watch. See [TESTING.md](TESTING.md) for current verification.

Full backups use the local export date and application name: `YYYYMMDD_ApplicationName_Description.json`, for example `20261111_KeepScore_Backup.json`. Completed-result interchange remains CSV; full backup JSON also preserves current rallies and Undo.

The landing page offers keyboard-accessible previews of all six scoreboards, supported formats, native menu/name examples, installation/update help and transfer privacy. Preview images use synthetic data, never real player exports.

## Complete backup and live audience

Settings → Backup & restore → Create or restore backup opens an encrypted ten-minute phone link. Download `YYYYMMDD_KeepScore_Backup.json` or choose a saved backup and send it. Receive changes on the watch, review, then explicitly Replace data & restore. This replaces all sport history, players, own name, favourites, colour/settings and the current match including Undo. It keeps the previous data as a downloadable recovery copy; restored unfinished matches are paused. Network keys, permissions and diagnostic logs are excluded. JSON backups are private user files and must be kept safely.

Current match → Live scoreboard → Start live sharing creates a separate read-only audience QR link for only this match’s score, names, rules and serving cue. Share the complete audience link; the phone-transfer link is private. A live link lasts eight hours. The watch pushes score/session changes and retries the latest queued score after connectivity returns; scoring never waits for internet. Visible audience pages refresh every three seconds (ten after completion) and pause requests when hidden. Stop sharing revokes access when online; offline revocation can fail and the link remains until expiry. Completion remains viewable; replacement of an unfinished match revokes sharing. A confirmed discard replaces the shared score/names with an anonymous “Match discarded” notice until link expiry, with no saved watch result. If offline, that notice is queued in memory and retried on reconnect. Task dismissal stops sharing. Expired live rows are physically cleaned on later link creation, not by an exact-time erasure job.

A completed result offers Share result card, opening the private phone view with Save/Share result-card image actions. PNGs contain only the selected match. Wear OS tile/face displays are event updated, with no periodic watch polling; tapping opens the match and preserves pause.

The audience page adapts to portrait phones and short landscape screens. Portrait keeps two large aligned cards; landscape places match details and controls alongside them. Full team names wrap, serving has its own badge, and viewers independently choose Classic/Neon Lime/Sunlight. Browser QA covers actual 393×852 and 852×393 CSS viewports with synthetic doubles names, aligned scores, all controls reachable and no horizontal overflow. These are browser layout checks, not physical phone acceptance.

Settings groups Display, Feedback, Data, Watch shortcuts and App controls. Vibration, reminder, screen and palette selectors save in place and show the selected tick until Back to Settings; button, hardware Back and swipe share that route. Selecting an already selected option does not navigate. Live sharing opens its audience QR automatically when ready while the waiting page remains visible; moving elsewhere prevents a late QR interruption.
