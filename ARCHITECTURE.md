# Architecture

Current watch release: **v0.16.2**, code **20**. This document describes the implemented system; release changes are in [CHANGELOG.md](CHANGELOG.md).

## Components and responsibilities

| Area | Implementation | Responsibility |
| --- | --- | --- |
| Watch UI | `android/src/at/andrew/squashtap/MainActivity.java`, `ScoreView`, `TouchTargets`, `ManualEntryUi` | Native round-screen scoring, dialogs, menus, input, contextual navigation and storage orchestration. |
| Scoring/model | `MatchFormat`, `ServiceOrder`, `ScoreState`, `MatchState`, `MatchResult`, `MatchBook` | PAR-11/deuce, BO3/BO5, rally/entered-set Undo, retirement, persistence, timing and result references. |
| People/interchange | `OpponentDirectory`, `ResultFilter`, `MatchStatistics`, `MatchCsv`, `ManualMatchEntry` | Stable names, filters, derived totals, validated CSV and independent past-match drafts. |
| Availability | `MatchDisplayPolicy`, `MatchSessionService`, AndroidX ambient/ongoing integration | Explicit active-session policy, quiet return shortcut and scoreboard screen flags. |
| Updates | `UpdatePolicy`, `VerifiedDownload`, `UpdateClient`, `InstallReceiver` | Manual update checks, bounded verified download, package/signer checks and installation. |
| Diagnostics | `AppDiagnostics`, `BatterySnapshot`, `DiagnosticTransfer` | Bounded local lifecycle/crash/exit and whole-watch battery evidence; explicit phone export. |
| Phone UI | `public/transfer.mjs`, `transfer-state.mjs`, `transfer-view.mjs`, `interchange.mjs` | In-memory browsing/statistics, CSV and staged name/import changes. |
| Server | `worker/index.ts`, `worker/relay.mjs`, `db/`, `drizzle/` | Vinext Worker, short-lived D1 metadata and R2 ciphertext. |
| Release tooling | `scripts/build.py`, `androidx_deps.py`, `test_ui.py`, `package_source.py` | Pinned builds/tests, original release signing, retained-APK verification and sanitized source exports. |

The website is a Vinext/Cloudflare Worker application with `DB` and `BUCKET` bindings. Canonical static assets are in `public/`; `dist/` is generated. GitHub Pages alone cannot run the transfer relay.

## Sport navigation and discard

MainActivity keeps the browsing scope separate from the one current match. An unfinished scored, timed, active, paused or explicitly prepared match locks every other sport. Selection/setup/create callbacks enforce the lock before mutation; completion or confirmed discard unlocks it. `match_ready_id` retains a created or explicitly started, unplayed match across restarts and foreground-session removal. It is not a result or active-service token. SportIcons supplies the same accent and vector mark to selectors, badges and ScoreView. Shared Players/Settings and their subpages identify the app as Keep Score; sport-scoped badges retain the sport name/icon. Shared menus retain contextual return callbacks; rule/mode/length/display choices use ticked lists with options before help text and restored setup scroll.

Discard clones the book and directory, calls `MatchBook.discardCurrent` and `OpponentDirectory.forgetMatch`, then commits both plus cleared active/pause/prepared preferences before swapping live references. It creates no result or daily reference. Saved history, counters, player catalog and historical roles stay intact; a fresh empty UUID prevents old Undo/resume/transfer callbacks from reviving the discarded match. Completed matches reject discard and retain the normal result-deletion path. New-match creation likewise commits related state before replacing live references. These navigation operations preserve the compatible book readers. Pickleball extends the nested ScoreState encoding and live sharing adds an independent relay table.

## Local persistence and compatibility

`SharedPreferences("squash")` contains the match book, separate opponent directory and display/session preferences. State commits precede successful result/apply confirmation. Failed commits retain the previous state and report the failure.

| Format | Current contract |
| --- | --- |
| Match book | Write B4 for unchanged squash-only state; B5 for explicit serving/multisport state or results. Read B1/B2/B3/B4/B5. Current match, completed results, timing, durable daily counters and the deleted-current marker are one unit. |
| Match/set encodings | Preserve M1 for ordinary squash, M2 for retirement-only squash, and M3 for entered squash totals. M4 stores explicit formats plus initial service choices for each game/set. `ScoreState` keeps real rallies or validated `2|me|opponent` completed totals. |
| Opponents | O1 or O2 with stable IDs (O2 adds separate partner/opponent-2 role maps; O1 stays readable), NFC-normalized optional names up to 60 code points and archive flags. Commit related match/name edits together. |
| CSV | Schema 1 for ordinary squash; schema 2 appends `retired_player`; schema 3 adds exact format, mode, slots 6/7, point/tie-break/unfinished-game details and both extra team identities. Read all three and gate unsupported old-watch imports. |
| Migration/recovery | Retain once-only B1/B2/B3/B4 and O1 backups, legacy score data and recovery copies of malformed data. Never fabricate old timestamps or rally order. |

Duplicate/conflicting imported IDs, the active current ID and deleted-current tombstones must not overwrite or resurrect local state. Validate every CSV row first, preview against current data, then merge cloned books atomically. Imported matches allocate references beyond durable local counters; deleting results never resets those counters.

History is bounded to 10,000 results; the decoded match-book string is bounded to 5,000,000 characters. A full book keeps the completed scoreboard while the user frees space and explicitly saves. Player identities are bounded to 1,000; encoded directory data is bounded to 8,000,000 characters. O2 role maps are separate from primary assignments, avoiding ID collisions. CSV is bounded to 3 MiB, expanded transfer content to 4 MiB and compressed transfer content to 1 MiB; large histories need date-range exports before these limits.

## Scoring, retirement and timing

Me is always first in scores. Squash games use PAR-11/win by two. MatchFormat freezes each sport's validated rules; ScoreState handles rally games or nested tennis/padel sets. ServiceOrder derives next-server guidance from genuine scoring and initial choices. All presets and exclusions are in [RULES.md](RULES.md). BO3/BO5 changes retain UUID, points, sets, timing and Undo, but reject completed matches and a BO5→BO3 change after either player already has two set wins.

A real final point or confirmed retirement completes/saves the match and stops its active session. Retirement preserves actual points/sets and determines the match winner independently of the set lead. Undo retirement restores the prior state before ordinary rally Undo. Undo/re-completion reuses its original reference and cannot revive a deliberately deleted result.

`MatchBook` uses an injectable `java.time.Clock`. First live point starts timing; Start match only enables availability. A valid manually saved set supplies the save instant/zone only when no start exists. Existing starts survive corrections and reopening. A live winning point or retirement records a finish; a manually submitted winning set does not claim to know its actual finish. Independent historical capture remains date-only.

Duration includes pauses and set breaks. Recorded start zone controls display/filter days after travel. Same-day details show one date and HH:mm times; overnight results include an end date. Unknown legacy times and backward-clock durations remain unknown rather than invented or negative.

## Navigation and power

The entry is a six-sport selector, with shared Players/Settings. Within each sport: Current match (or new/resume), Results, Statistics, Change sport and Scoring rules. One current match remains global. Result mode/sport selection is separate from date/player filters; statistics ignore date/player filters. Global player icons derive involvement across all saved results, including partners. Results have 10 items per watch page; phone browsing has 25. Filters apply before pagination. Details/pickers/delete cancellation preserve list filter/page/scroll and the actual caller. Every named Back/Cancel, system Back and native swipe callback uses the same route. Late network callbacks cannot reopen an abandoned waiting panel.

Roles are explicit: blue match/secondary navigation, purple people, grey Settings, red destructive actions and outlined Back/Cancel. Touch targets are at least 48dp. Score taps reject swipes, long presses and multi-touch. New-match setup stages format/name choices until explicit Create; replacement requires confirmation.

Availability requires `active_match_id` equal to the unfinished current UUID and no pause. First point or Start/Resume activates it. Pause, completion, task removal and replacement clear it. Older unfinished books without a session wait for Resume; Undo after completion also waits. Undo to zero during active play retains the session. Ordinary activity pause/ambient does not impose an inactivity timeout during play.

Keep screen on applies only to a resumed active scoreboard; panels/backgrounding release its window flag. QR screen retention is limited to one minute and releases on backgrounding. Idle drafts/results/statistics do not create a session. No health/sensor sampling, GPS, background update polling, telemetry or app-held CPU wake lock is added. Battery snapshots are event/on-demand evidence for the whole watch, not attributed app energy use.

## Encrypted transfer boundary

1. An explicit watch action creates a random session ID, AES-256 key and separate owner/guest capabilities, held in activity memory.
2. The watch encrypts a bounded gzip snapshot with AES-GCM (random 12-byte IV, 128-bit tag). AAD binds session ID and direction: `squashtap-transfer-v1|sessionId|direction`.
3. The QR's HTTPS fragment carries key/guest capability and a whitelisted local phone tab. The fragment is not sent in the page request; unknown/old view values default to Results.
4. The phone fetches ciphertext with its guest capability, decrypts locally and browses/filters/statistically analyzes that snapshot in memory.
5. Explicit one-shot incoming submission is encrypted. The watch separately receives, previews and applies it. Adding/restoring names does not assign an opponent without explicit opt-in.
6. End/successful apply requests deletion; ten-minute expiry refuses later access. Unused expired objects/rows are cleaned in bounded batches on subsequent API traffic.

D1 stores session metadata, hashes of capabilities, expiry/pending state and short-lived hashed-IP rate buckets; R2 stores opaque ciphertext. No plaintext names/history, AES keys or raw-IP logs are required. Creation is bounded to 12 attempts per ten-minute IP bucket and 250 concurrent sessions. Requests are fixed-origin HTTPS, redirect-disabled and bounded; native connect/read timeouts are 12/20 seconds. Authorization headers carry capabilities after creation; responses use no-store and do not grant CORS access. There is no public list endpoint.

This is link-possession access, not identity-based authorization: anyone with a complete active QR link can access its transfer. Access expiry does not guarantee exact-time physical deletion, provider-backup erasure, revocation of screenshots or deletion of downloaded CSVs. It is not a permanent backup service. Diagnostics use a separate fragment decoder and no diagnostic-upload endpoint.

## APK updates and evidence

Update metadata is untrusted. Validate schema/package/version/SDK, HTTPS origin, expected size and SHA-256. Streaming download enforces size/hash; the APK must match the package and installed signer before PackageInstaller confirmation. Settings initiates the request; no launch/scheduled polling exists. Keep the original package and release key for official in-place updates.

See [TESTING.md](TESTING.md) for the recorded 400-check v0.13 release, separately recorded user reports and remaining physical-device acceptance. Source publishing and documentation checks do not establish physical screen, sensor, gesture or battery behavior.

## Serving display and landing previews

ScoreView reserves a separate high-contrast serving capsule and outline on the serving score card. Name labels fit actual measured card width with Unicode-safe truncation; the serving capsule uses fixed roles rather than variable-length names. Derive the service slot once per frame, retain known service in ambient text and expose complete server names in accessibility text. The colour palette changes drawing only; the recorded sport/rules/points and serving derivation remain independent.

The landing page keeps its canonical HTML in public/index.html with matching app/layout.tsx metadata. Native Canvas previews generated from synthetic fixtures are copied to public/watch-*.png and included in sanitized source exports. Native radio inputs and CSS switch the six hero previews without JavaScript, storage or network transfers.

## Keep Score v0.16 components and protocols

`PickleballGame` replays the actual rally sequence into point totals and service state. ScoreState encoding 4 stores pickleball format, both chosen starters and genuine rallies or entered totals. M4/B5 continues to wrap it; existing B1–B5 and score encodings remain readable. Unknown old starters are not invented. Non-scoring side-outs are real Undo events and start timing. `MatchPresets` stores at most 24 named immutable presets under `favourite_formats`; `my_name` is separate from O1/O2 identities.

`ScorePalette` supplies Classic/Neon Lime/Sunlight colours to the Canvas scoreboard. `score_palette` is validated and backed up; missing/unknown local preferences fall back to Classic. Menu sport colours and classic result cards retain their existing roles. Canvas uses the system font scale and measured fitting; round panels use proportional insets, square/rectangular panels use compact insets. `TouchTargets.area` guarantees 48dp Menu/Undo/New hit areas. Ambient remains monochrome. The app leaves Window brightness at its system default; Display settings is an explicit external intent.

`MatchHaptics` uses short vibration patterns on accepted point/game/set/match transitions with user and system controls. `MatchReminders` derives optional brief prompts from state transitions, without timers that govern play or stored counters. `MatchWidgets`, `KeepScoreTileService` and `ScoreComplicationService` publish read-only current scores on changes; Tile freshness and complication update period are zero, so there is no periodic watch update loop. Their explicit tap intents preserve session/pause state. Tile/complication services require the Wear system’s binding permissions.

`BackupArchive` schema 1 contains a checksummed base64url binary payload of the full validated match book, player directory and allowlisted settings. JSON maximum is 20 MiB, with bounded book/directory/settings fields. It excludes keys/capabilities, diagnostics, notification permissions and active session tokens. SHA-256 detects corruption, not malicious authorship; imports require native structural validation and watch confirmation. `backup_before_restore` holds one pre-restore recovery copy and is downloadable separately. Restores atomically commit all related preferences, reject stale book/directory/settings or expired sessions, clear the active token and pause unfinished matches. CSV remains completed-results interchange.

Ordinary encryption envelope 1 retains 4 MiB plaintext/1 MiB compressed limits and its original AAD. Full-backup envelope 2 permits 24 MiB plaintext/4 MiB compressed and appends `|backup-v1` to the AAD, preventing version relabelling. Relay request bounds are 6,000,600 bytes; envelope-specific ciphertext bounds remain enforced. Both use AES-256-GCM/gzip, ten-minute expiry and the existing role/capability boundary. Phone capabilities gate backup, pickleball and own-name editing for old clients.

`LiveScoreClient` is an application-memory singleton with a single network executor, latest-state coalescing and connectivity callback retries. It has no watch polling/heartbeat. `/api/live` stores opaque encrypted score snapshots in D1 `live_games` (schema migration 0001), separate from the transfer history snapshot. Random owner and viewer capabilities have independent hashes; only the watch owner can PUT/DELETE, only the viewer can GET. Keys stay in URL fragments/memory. Snapshots contain only one match’s score/rules/names/serving/phase. Bounds: eight-hour TTL, 32,000-character envelope, 48,000-byte body, 250 rooms and 12 creations per ten-minute hashed-IP bucket. Revisions prevent delayed writes replacing newer ones. Expired access is rejected; physical cleanup occurs on subsequent creation. No list endpoint, CORS access, caching or plaintext cloud history is added.

Audience pages poll only while visible (three seconds, ten after finish), preserve the last received score through connection failures and show its timestamp. They cannot know whether an idle watch is disconnected or simply between rallies. Offline stop cannot guarantee immediate remote revocation; the watch reports that limitation. Completed scores can remain until expiry; unfinished replacement/restore/task dismissal revoke sharing. A confirmed discard replaces the snapshot with the encrypted minimal notice `{schema:1, phase:"discarded"}`, containing no score or names. Process death drops all local capabilities and expiry bounds leftover remote links. Local development fixtures for phone/live use synthetic data and are tree-shaken out of production.

`result-card.mjs` renders one completed result into a 1080×1280 PNG with full names, sets, timing and Keep Score branding. Download/Web Share is explicit; the watch points at a focused private phone snapshot, never a public history URL.

The audience page adapts to portrait phones and short landscape screens. Portrait keeps two large aligned cards; landscape places match details and controls alongside them. Full team names wrap, serving has its own badge, and viewers independently choose Classic/Neon Lime/Sunlight. Browser QA covers actual 393×852 and 852×393 CSS viewports with synthetic doubles names, aligned scores, all controls reachable and no horizontal overflow. These are browser layout checks, not physical phone acceptance.

`MainActivity.onLiveStatusChanged` reconciles session identity/readiness into visible controls, ignores abandoned panels/background callbacks and opens Audience QR once from its pending state. Settings uses persistent choice pages and contextual named Back actions. Reselecting an active option never implicitly goes Back.

`LiveScoreClient.discard` runs only after the watch discard commit. It removes that session from active sharing, clears its plaintext score and queues an anonymous terminal update using the existing owner PUT/revision/encryption protocol. Up to eight notices can remain in process memory; connectivity callbacks retry them without polling. Success/expiry releases pending callbacks when otherwise idle. No keys or notices are persisted across process death. The relay schema is unchanged and opaque ciphertext replaces the old score. The viewer accepts only the two-field discard notice, removes rendered names/scores, shows Match discarded and stops refreshing. HTTP 401/403/404 likewise clear stale scores and show an ended broadcast; transient network errors still preserve the last score.
