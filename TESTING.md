# Testing and verification

## Automated checks — v0.13.0

**265 JUnit tests pass** against the production shared-core classes:

| Area | Tests | Coverage |
| --- | ---: | --- |
| ScoreState | 23 | PAR-11, deuce, Undo, winners, bounds, malformed persistence and 1,000 seeded random game round-trips. |
| TouchTargets | 10 | Card/header/footer geometry, ordinary taps, swipe/long-press rejection. |
| UpdatePolicy | 22 | Version/SDK/app identity, HTTPS/host restrictions, malformed responses, bounded metadata, hash validation. |
| VerifiedDownload | 9 | Streamed checksum/size/progress, corruption, truncation, overrun, connection and storage failures. |
| MatchState | 26 | BO3/BO5, set transitions, final winner, Undo across sets, legacy-score migration, malformed persistence and guarded format changes with retained points/Undo. |
| MatchBook | 36 | Saved results, set summaries, local daily numbers, correction/re-completion, deletion/tombstones, restart, full history, invalid data, format corrections and saving results under the new format. |
| MatchDisplayPolicy | 16 | Explicit current-match session ID; idle/legacy/stale/missing IDs; ongoing and screen-power rules for active, paused, complete, ambient, background and modal states. |
| MatchTiming | 28 | First-point start, final-point finish, elapsed formatting, restarts, Undo/format/new-match/deletion, migration fixtures, local display, midnight/DST/timezone changes, clock corrections and corrupt timing rejection. |
| CompactMatchTiming | 7 | One same-day date; short times; overnight/year-boundary end date; recorded timezone after travel; missing times; clock reversal. |
| MatchStatistics | 7 | Mixed BO3/BO5 and deuce scores, Me perspective, empty/legacy/unfinished history, import/persistence, deletion and Undo/re-completion/tombstones. |
| MatchRetirement | 17 | Both participants, actual partial/between-set scores, timing/Undo/deletion/references, stats, CSV/import/dedupe, invalid outcomes and real B2 migration fixtures. |
| ManualMatchEntry | 27 | Score-only sets, invalid/deuce/boundary scores, live/manual progression/Undo/timing, independent drafts, dates/edits, retirement/deletion and real B3 migration. |
| OpponentDirectory | 8 | Stable identities, Unicode/NFC, rename, removal/history retention, catalog replacement, duplicates, bounds and persistence. |
| MatchCsv | 13 | RFC quoting/BOM/semicolon, spreadsheet escaping, timing/score validation, merge/deduplication/conflicts, reserved current IDs, reference counters and legacy missing times. |
| ResultFilter | 5 | Combined inclusive date/opponent filters, removed/unnamed identities, recorded timezone/day and range validation. |
| TransferCrypto | 7 | Random IVs, Unicode round-trip, authenticated key/context/tamper rejection, bounds, fingerprints and browser golden vector. |
| DiagnosticTransfer | 4 | Compressed Unicode round-trip, fragment payload, HTTPS validation and size bounds. |

The build verifies the new APK signature against the original certificate, package/version/watch metadata and ZIP alignment. Retained APK hashes (v0.3.0 onward) are checked and preserved. Distribution retention checks verify v0.1/v0.2 are absent from APKs, metadata, checksums and page links, while retained binaries and release.properties remain byte-identical. This cleanup does not rebuild the Android app. The public report is `public/test-results.txt`; metadata and hashes are in `public/builds.json` and `public/SHA256SUMS.txt`.

A browser check verified phone-side decoding of a synthetic Unicode report, fragment removal, the clipboard-unavailable fallback and invalid-payload handling. The cloud browser cancelled the TXT download; saving/sharing a file and optical QR scanning remain phone/watch acceptance checks.

## Historical physical-device evidence

On 3 November 2026 the user reported successful phone-only installation with Wear Installer 2, working basic operation, enabling allow-updates, in-app update **v0.1.0 → v0.2.0** and retention of the existing score on the OnePlus Watch 2R / Wear OS 5. Undo-history retention was not separately confirmed. These user reports are recorded in `DEVICE_TESTS.json` and do not apply automatically to later releases.

The user subsequently reported that v0.2 returned to the watch face/closed during a match, including with Keep screen on enabled. Without logs, a normal watch-face timeout, activity closure and process crash cannot yet be distinguished. v0.3 adds ambient/ongoing support and diagnostics to investigate this.

The user later confirmed installing v0.3, then reported that match results were mixed with general Settings and the BO3/BO5 selector was hard to find. v0.4 separates navigation and adds an explicit in-place format selector. This feedback does not confirm the idle/ambient or QR transfer fix.

## Android navigation/style checks

**100 Robolectric tests pass** on API 34 using the real MainActivity, Dialogs, Buttons, service implementation and compiled AAPT resources. Ten navigation/style/timing checks cover:

- Root title is Menu, with Current match, Results, Statistics, purple Opponents and grey Settings; updates stay out of match navigation.
- Current match and Match options contain no app settings.
- Settings actions stay neutral grey in enabled, pressed and disabled states; Back is outlined.
- The update page has grey actions and outlined Back navigation.
- Choosing BO5 through actual menu/button navigation retains points and past results.
- Single-result deletion and its confirmation are red; outlined Cancel retains the result.
- Delete-all and its confirmation are red and preserve the active score.
- Saved results use coloured cards rather than grey buttons.
- Result headings use start time, with correct start/finish/duration and the secondary match reference.
- Opening an actual B1 fixture preserves the backup, upgrades to B4 and shows unknown old times honestly.

Fifteen session/power regressions in SessionPowerUiTest cover:

- Fresh launch has no screen flag, service start or app wake lock; advancing the looper ten minutes triggers no update check.
- The first point starts a session; Undo back to 0–0 retains that explicit session.
- Start match before a point activates availability without inventing a start timestamp.
- Pause releases screen/service; reopening retains score/timing and requires Resume. A scoring tap while paused opens Match without adding a point.
- An older unfinished saved book with no session ID waits for Resume and retains Undo.
- Match completion clears the session; navigating history/Settings cannot restart it.
- Undo of a completed match waits for Resume.
- Committing a new match ends the previous session and waits for play.
- Ordinary activity backgrounding releases the screen flag while preserving a real match; resuming restores it.
- Results/Settings release the screen flag; Back restores it only for active play.
- A null-intent sticky service restart with an old score but no session ID stops itself with START_NOT_STICKY and no foreground notification.
- A valid service can run; explicit task dismissal clears its ID, pauses and stops foreground mode; a later sticky restart stays stopped.
- Diagnostics and activation/pause events use on-demand whole-watch battery snapshots; unsupported capacity is labelled unavailable.
- The QR's screen-on flag expires after one minute and clears immediately on backgrounding.

Run `python3 scripts/test_ui.py --version 0.13.0` after resources are built. The default release builder runs these checks before D8/signing. Test-only Maven 3.9.9/Robolectric 4.14.1 dependencies and Android framework jars are cached under ignored `.local/` and are not bundled in the watch APK. API 34 executes tests; Robolectric uses an API 35 framework jar to parse resources. Proxy settings, if needed, are passed through an anonymous memory file, not stored credentials. See `android/ui-test/pom.xml` and `scripts/test_ui.py`.

These are headless Android UI checks, not a Wear OS emulator or physical display capture. They verify routing, button roles/state colours and persistence through real view actions; they do not prove optical display fidelity, firmware ambient transitions or battery use.

## v0.4 watch reports — 3 November 2026

The user confirmed v0.4 installation and extracted a diagnostic report through the watch QR code. QR scanning/phone report extraction is passed; saving a TXT was not separately confirmed. The user reports the app has stayed in the foreground since v0.3. Detailed ambient transitions and battery measurements remain open.

The supplied report contained no captured Java crash. Its package-update exits and ordinary pause/resume events do not establish a malfunction. The force-stop entry does not identify who requested it, and battery exemption false alone does not establish a cause. Do not publish the raw report; DEVICE_TESTS.json contains aggregate observations only. v0.5 labels update exits and logs page/window context for future reports.

The user also reported confusing Match menu naming/Settings colours and requested red delete actions and distinct Back styles. v0.5 addresses those with explicit action roles and the UI checks above.

The user approved v0.5’s cleaner navigation/design and requested start/finish timestamps and duration for results. The v0.6 model/UI changes are covered by the new timing/migration tests.

## Current watch acceptance

1. Update in place and confirm v0.13.0 under the grey MENU → Settings entry. The root must say Menu and show Current match, Results, Statistics, Opponents and Settings. Any older unfinished score must wait for Current match → Resume match, keeping its points, Undo, sets and timing.
2. Open Current match and Match options: scoring/navigation controls use blue and contain no updates. Open Settings: its update/display/diagnostic actions are neutral grey. All Back/Cancel controls are outlined; check the QR page’s return control too.
3. With a disposable result, verify both single/all delete entry and confirmation actions are red. Cancel once before confirming. Check the active score remains and deletions persist after reopening.
4. Open MENU → Current match → Match options → Match format; change an unfinished BO3 to BO5 and verify points/Undo remain. Check Next/set history, final result, date/number and winner-coloured cards during ordinary play.
5. Verify foreground retention continues during a started match. Separately exercise Keep screen on OFF/ambient and the ongoing shortcut, then pause/resume under Current match. Recheck after Settings/Back, including hardware Back. On Pause/completion, the ongoing shortcut should disappear and the watch should sleep/return home normally; reopening results must not restart it. A new draft stays idle until the first point or Start match.
6. If sharing a new diagnostic report, check readable App updated labels, page/window context and complete trace lines. Re-scan the QR; TXT save/share is still worth checking on the phone.
7. Start a fresh match. Verify the start is the first scored point, not setup, and survives reopening. Finish it after a known interval; check time title, Started/Finished timestamps, duration, date and secondary number. Undo/re-win the last point and check finish/duration update. Old untimed results should say Not recorded; an ongoing match begun before the update must not invent a start.
8. Check Diagnostics for the whole-watch battery percentage/charging state and session-active status. Compare a paused/finished idle period of 30–60 minutes with a similar period when the app has not been used, under comparable brightness/AOD/network conditions. Turn off wireless debugging for the comparison. Test active play separately. Record start/end battery/time and charging state; a percentage drop includes all watch activity, so cannot prove app-only consumption. No physical battery measurements have been supplied yet.
9. Leave the diagnostic QR open: its forced-screen-on flag should release after one minute. Returning home should release it immediately. Opening the app alone should no longer initiate an update request; Settings → Check for updates must still work.

Public-page checks cover concise feature copy, current download/installation links, responsive layout and absence of chat/debugging/internal-test narratives on the landing page. The update feed points to v0.13.0; retained v0.3–v0.12.1 APKs remain byte-identical.

## v0.8 transfer/opponent verification

Twelve additional Robolectric checks execute opponent management/add/picking/rename/delete roles, scoreboard accessibility, combined opponent/custom-date filters, invalid-range rejection, system speech intent/confirmation, actual phone-preview Apply/Cancel, stale fingerprint rejection and refusal to silently reuse an open transfer for a different export selection. The apply test verifies the active score and session remain, and credentials never enter preferences. Cleanup network work is queued by the test executor rather than contacting the public service.

Twenty-one Node/Miniflare tests pass: eleven browser interchange checks exercise native-to-browser and browser-to-native authenticated encryption, wrong context/key/tamper handling, Unicode/CSV quoting, formula protection, semicolon/BOM, invalid scores/dates/duration and size bounds. Ten relay tests use real local D1/R2 bindings for creation/hashed capabilities/opaque payloads, role isolation, missing/incorrect authorization, one-shot receive, deletion, expiry/cleanup, origin/input/size limits, rate bounds and no-store/no-CORS headers. Run node --test web-test/*.test.mjs after core compilation. The default release builder runs the same checks before signing.

The supported framework build succeeds with public assets and D1/R2 declarations. A managed-browser check verified the preserved landing page and transfer entry/error view. HTTP previews cannot use Web Crypto; no insecure-context workaround is added. The dev-only synthetic-watch helper exists for local HTTPS/secure-context testing and must not be present in the production bundle. Record any additional live HTTPS browser observations separately; headless storage tests alone are not a physical watch transfer pass.

Additional physical acceptance:
1. Update to v0.8 without uninstalling. Existing results, current score/Undo/timing and battery policy remain. Existing results have no invented opponent.
2. Select No name, then add/select an opponent. Verify the red card and result use the chosen name. Test Speak name with spelling confirmation and the phone keyboard alternative.
3. Rename a person and verify linked history. Delete their picker entry using red confirmation, verify results survive and remain filterable; restore on the phone if desired.
4. Combine a person and inclusive date range, including Unnamed and a legacy untimed result. Verify export selection matches the chosen results.
5. Open Results → Options → View results on phone and scan its QR. Download a CSV on the real phone. Prepare name edits and receive/apply them; verify the exact changed names shown in the watch preview. Cancel once and confirm it applies nothing.
6. Reimport the CSV; duplicates must be skipped. Try an invalid/changed same-ID row; it must fail validation or show a skipped conflict, never overwrite the saved result/current match. Verify references/Undo/deletion after reopening.
7. Check a ten-minute expired/ended link and a one-shot second send. A stale local catalog/new match requires a fresh session. Reopening/killing the activity requires a new QR, not a persistent credential.
8. Check ordinary play retention, pause/finish battery policy and QR timeout. Transfer calls must occur only after explicit actions; no background receive polling is expected.

No user report yet confirms v0.8 phone file picking, downloads, dictation availability, optical QR scanning, real transfer latency or physical battery use. DEVICE_TESTS.json remains historical aggregate evidence only.

## v0.9 optional-name regression checks

Ten additional Robolectric tests exercise person-row normal/pressed/disabled styling, icons and alignment; match/filter name-field roles; skipping names without inheriting the previous opponent; staging then confirming BO5 with a future named result; cancelling setup and replacement without altering the old score/person/session; returning from a cancelled picker; clearing a staged person; creating a new name during setup without assigning the old match; and starting from an empty directory without creating a person. Together there are 47 Android UI tests, 207 shared-core tests and 21 browser/relay tests (275 total).

The user reported that v0.8 worked and requested this UI refinement; DEVICE_TESTS.json records only that general confirmation. Actual purple styling and optional setup on the physical OnePlus remain acceptance checks for v0.9. There is no new CSV/backend/schema change and no physical battery claim.

## v0.10 statistics and timing regressions

Fourteen core tests and seven Robolectric view tests were added. The latter open Statistics through the real root menu, verify match/set/point counts, signed balances and rounded win rate, preserve score/opponent/session state and result filters, exclude filtered-history scope from overall totals, recalculate after real deletion, and render compact same-day/overnight/legacy details. Same-day completed-detail text contains its date exactly once while duration, set scores and daily number remain. An empty history shows no percentage estimate. Opening an idle Statistics page starts no service.

Current suite: 221 core + 54 Android UI + 21 browser/relay = 296 checks. Native physical OnePlus readability and the new screens remain acceptance checks; no new runtime dependencies, schemas, backend operations or battery claims are introduced.

## v0.11 retirement, names and phone history checks

Seventeen core regressions cover retired state/result validation, real scores and outcomes, timing, Undo/re-completion, tombstones, mixed schema-2 CSV, imports and three authentic B2 fixtures generated with the v0.10 implementation before changing its serializer. Existing B1/timing/counter tests still run under B3.

Twelve new Robolectric checks cover Match-only End navigation, both cancellation stages, either player quitting, no phantom sets, power/session release, Undo without losing a rally, once-only B2 backup, catalog-only sync on the last completed match, strict explicit reassignment with highlighted preview, Browse on phone and snapshot scope/retirement metadata.

Twelve new Node checks cover two-way native/browser retirement CSV, adding/restoring multiple names without match assignment, explicit reassignment/clearing, retirement-aware all-player and head-to-head totals including unnamed history, zero history, older-watch retirement gating, invalid CSV progression and non-mutating history sort. The existing crypto and D1/R2 relay suite remains. v0.11 total: 238 core + 66 Android UI + 33 browser/relay/state = 337 checks.

Production HTTPS phone-page verification uses synthetic sessions only; never inspect real user transfer payloads or keys. Check additions leave the match selector unchanged; opt-in permits assignment; overall/per-opponent W/L and recent-form links are correct; opponent/date filters and older pages work; retired details show real scores. End test sessions afterwards. Native physical OnePlus layouts, optical QR and quitting during actual play remain separate device acceptance checks.

## v0.12 keyboard-free manual entry

Twenty-three new core tests cover entered totals without synthetic rally order, validation/deuce/max bounds, reset, BO3/BO5 progression, replacing only the current partial set, whole-summary versus genuine rally Undo, draft edits/format/date validation, independent history/CSV, honest missing timing, deleted-current protection, retirement mixtures and authentic B3 fixtures generated from the previous compiled app.

Fourteen new real Android view tests exercise Match-only navigation, blue/red winner selectors, wheels with blocked descendant focus and hidden soft input, presets/+−/deuce preview, opponent-first winner with Me-first score order, cancellation at each stage, final-set completion/power release, whole-set Undo confirmation, independent historical save/opponent/date/current-session retention/idempotency, BO5 unnamed entry, date wheels/leap clamp/cancellation, B3 backup retention, full-draft cancellation, continuing live while preserving the manual-set save time and idle historical entry without a service.

Total: 265 core + 100 Android UI + 35 browser/relay cases = 400 checks. No new CSV/relay schema or runtime dependency is required. Native OnePlus round-screen appearance, wheel gestures and comfort of the larger controls remain physical-device acceptance checks; no physical display test is claimed.

## v0.12.1 manual-set start regression

Four new core cases and the updated manual/live timing case verify: rejected scores leave an unset start untouched; accepted entry uses the injected clock and local zone; later entries/Undo/restart preserve an existing start and zone; authentic untimed B4 data from compiled v0.12 gains a start only on the next accepted entry; and new-match timestamps do not change saved results. The fixture book-v4-manual-untimed.txt was generated before editing MatchBook using build/0.12.0/classes.

Two new Robolectric cases and the updated Continue test exercise the real confirmation/cancel/save controls, save-time bounds, persistence after reopening, prior untimed data, the missing-start hint and continued idle power behaviour. Historical whole-match times remain unknown in the existing suite. Total: 265 core + 100 Android UI + 35 browser/relay = 400 checks. Physical watch clock/display acceptance remains separate.

## v0.13 navigation regressions

Eighteen new MenuRevisionUiTest cases execute native buttons/dialogs with compiled resources: five root destinations and explicit colour roles; active/paused/complete match actions; staged Create/replacement/caller cancellation; button/system Back equivalence; SwipeDismiss callback routing; diagnostic QR return/flag release; filter/page/scroll retention through details/person/delete cancellation; bulk deletion placement; separate live-set versus past-result entry; contextual phone views/returns; late creation completion; light navigation rows with large targets; and Scoreboard shortcuts that preserve pause plus Statistics return context. Existing 82 tests were updated to use the new explicit paths and labels without dropping their data/power assertions.

Two browser-state tests whitelist local launch tabs and reject unknown/untrusted values with the legacy Results fallback. Current suite: 265 core + 100 Android UI + 35 browser/relay = 400 checks. Native swipe callback execution validates wiring; optical QR, physical swipe recognition, round-screen comfort and battery measurements remain device acceptance.

Headless visual QA also rendered eight real Android panels at 233 dp using Robolectric native graphics: Menu, Current match, Match options, New match, set entry, Results, Result options and Settings. The Results toolbar was tightened after inspection and re-rendered; its three touch targets remain at least 48 dp. This is separate from the 400-check release suite and from physical round-watch acceptance.

## Website-only privacy/download review — 7 November 2026

Rerun the existing 35 browser/crypto/relay tests. A separate synthetic Miniflare two-user probe creates two encrypted sessions, verifies each intended read, then rejects cross-session read/write/delete, wrong-key and wrong-context decryption; confirms the absence of a list endpoint and removes both fixtures. No real user transfer data is accessed. Check the landing page links only the current APK, the generic transfer link is absent, privacy/expiry/link-possession copy is visible and all 12 retained APKs, inventory/checksums and current update feed are unchanged. Android is not rebuilt for this copy change; its previously verified 400-check release remains v0.13.0.
