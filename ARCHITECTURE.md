# Architecture

Current watch release: **v0.13.0**, code **14**. This document describes the implemented system; release changes are in [CHANGELOG.md](CHANGELOG.md).

## Components and responsibilities

| Area | Implementation | Responsibility |
| --- | --- | --- |
| Watch UI | `android/src/at/andrew/squashtap/MainActivity.java`, `ScoreView`, `TouchTargets`, `ManualEntryUi` | Native round-screen scoring, dialogs, menus, input, contextual navigation and storage orchestration. |
| Scoring/model | `ScoreState`, `MatchState`, `MatchResult`, `MatchBook` | PAR-11/deuce, BO3/BO5, rally/entered-set Undo, retirement, persistence, timing and result references. |
| People/interchange | `OpponentDirectory`, `ResultFilter`, `MatchStatistics`, `MatchCsv`, `ManualMatchEntry` | Stable names, filters, derived totals, validated CSV and independent past-match drafts. |
| Availability | `MatchDisplayPolicy`, `MatchSessionService`, AndroidX ambient/ongoing integration | Explicit active-session policy, quiet return shortcut and scoreboard screen flags. |
| Updates | `UpdatePolicy`, `VerifiedDownload`, `UpdateClient`, `InstallReceiver` | Manual update checks, bounded verified download, package/signer checks and installation. |
| Diagnostics | `AppDiagnostics`, `BatterySnapshot`, `DiagnosticTransfer` | Bounded local lifecycle/crash/exit and whole-watch battery evidence; explicit phone export. |
| Phone UI | `public/transfer.mjs`, `transfer-state.mjs`, `transfer-view.mjs`, `interchange.mjs` | In-memory browsing/statistics, CSV and staged name/import changes. |
| Server | `worker/index.ts`, `worker/relay.mjs`, `db/`, `drizzle/` | Vinext Worker, short-lived D1 metadata and R2 ciphertext. |
| Release tooling | `scripts/build.py`, `androidx_deps.py`, `test_ui.py`, `package_source.py` | Pinned builds/tests, original release signing, retained-APK verification and sanitized source exports. |

The website is a Vinext/Cloudflare Worker application with `DB` and `BUCKET` bindings. Canonical static assets are in `public/`; `dist/` is generated. GitHub Pages alone cannot run the transfer relay.

## Local persistence and compatibility

`SharedPreferences("squash")` contains the match book, separate opponent directory and display/session preferences. State commits precede successful result/apply confirmation. Failed commits retain the previous state and report the failure.

| Format | Current contract |
| --- | --- |
| Match book | Write B4; read B1/B2/B3/B4. Current match, completed results, timing, durable daily counters and the deleted-current marker are one unit. |
| Match/set encodings | Preserve M1 for ordinary matches and M2 for retirement-only state; M3 can carry entered set totals. `ScoreState` keeps real rallies or validated `2|me|opponent` completed totals. |
| Opponents | O1 with stable IDs, NFC-normalized optional names up to 60 code points and archive flags. Commit related match/name edits together. |
| CSV | Schema 1 for ordinary-only results; schema 2 appends `retired_player` for retired/mixed exports. Read both and gate unsupported old-watch imports. |
| Migration/recovery | Retain once-only B1/B2/B3 backups, legacy score data and recovery copies of malformed data. Never fabricate old timestamps or rally order. |

Duplicate/conflicting imported IDs, the active current ID and deleted-current tombstones must not overwrite or resurrect local state. Validate every CSV row first, preview against current data, then merge cloned books atomically. Imported matches allocate references beyond durable local counters; deleting results never resets those counters.

History is bounded to 10,000 results; the decoded match-book string is bounded to 5,000,000 characters. A full book keeps the completed scoreboard while the user frees space and explicitly saves. Opponent identities are bounded to 1,000. CSV is bounded to 3 MiB, expanded transfer content to 4 MiB and compressed transfer content to 1 MiB; large histories need date-range exports before these limits.

## Scoring, retirement and timing

Me is always first in scores. Sets use PAR-11/win by two. BO3/BO5 changes retain UUID, points, sets, timing and Undo, but reject completed matches and a BO5→BO3 change after either player already has two set wins.

A real final point or confirmed retirement completes/saves the match and stops its active session. Retirement preserves actual points/sets and determines the match winner independently of the set lead. Undo retirement restores the prior state before ordinary rally Undo. Undo/re-completion reuses its original reference and cannot revive a deliberately deleted result.

`MatchBook` uses an injectable `java.time.Clock`. First live point starts timing; Start match only enables availability. A valid manually saved set supplies the save instant/zone only when no start exists. Existing starts survive corrections and reopening. A live winning point or retirement records a finish; a manually submitted winning set does not claim to know its actual finish. Independent historical capture remains date-only.

Duration includes pauses and set breaks. Recorded start zone controls display/filter days after travel. Same-day details show one date and HH:mm times; overnight results include an end date. Unknown legacy times and backward-clock durations remain unknown rather than invented or negative.

## Navigation and power

Menus are Current match, Results, Statistics, Opponents and Settings. Results have 10 items per watch page; phone browsing has 25. Filters apply before pagination. Details/pickers/delete cancellation preserve list filter/page/scroll and the actual caller. Every named Back/Cancel, system Back and native swipe callback uses the same route. Late network callbacks cannot reopen an abandoned waiting panel.

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
