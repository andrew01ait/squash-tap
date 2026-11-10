# Releases

## 0.15.1 — 2026-11-10

- Keep serving visible with a dedicated pale-gold badge and score-card outline. Fit names by measured pixel width; fixed doubles role labels keep the serve cue independent of long or missing names. Preserve known service in ambient and full names in accessibility text; hide serve cues after a game/match finishes.
- Refresh the landing page around the five sports, native example previews, supported modes/rules, shared players/separate results, clearer navigation, installation/update guidance and transfer privacy. Preserve blue score/red playing branding and the single current APK version. Native radio/CSS preview switching needs no JavaScript. Update page metadata and export PNG source assets.
- Verify 299 core + 135 native UI + 43 web/relay checks = 477, native renders across four sizes, original signing identity/package/API/alignment and all 15 retained APK hashes. Update documentation and the matching public source package.


## 0.15.0 — 2026-11-10

- Identify each sport with its own vector mark and accent in the selector, menu badges and live scoreboard; retain blue/red scoring sides. Separate sport actions and app-wide controls, with shared purple Players, grey Settings, red discard/delete and contextual Back/Cancel.
- Lock other sports while a match is unfinished, including paused and explicitly created unplayed matches. Finish or cancel first. Preserve the lock across restart and reject stale sport-selection/new-match callbacks. Completed or discarded matches unlock selection.
- Add Current match → Cancel match → Discard match to quit without saving a result, retirement or win/loss. Commit the replacement state, abandoned role-link removal and cleared session/pause/prepared flags together; retain saved history, shared names and daily counters. Undo/resume cannot restore discarded rallies. Keep retirement as the separate Save retirement result action.
- Replace cycling mode/scoring/deuce/decider/result-mode controls with explicit selected lists. Put match length in its own picker and provide explicit screen-policy choices. Back/swipe/cancellation and reselecting the same choice keep the draft and serving choices.
- Return shared Players/Settings to the sport selector or sport Menu that opened them, prevent unrelated score shortcuts, and label tennis/padel’s next opposing server correctly. Make new-match replacement commits atomic before replacing the in-memory state.
- Put choices before help text, preserve setup/options/settings/serving scroll on picker return, and omit the Scoreboard shortcut from confirmation questions. Verify 299 core + 130 native UI + 43 web checks = 472, inspect round-screen native renders, and retain original signer/package/API/alignment and historical APK hashes. Physical watch acceptance remains separate.


## 0.14.0 — 2026-11-10

- Add the five-sport entry selector: squash, tennis, badminton, padel and table tennis. Keep one current match, explicit replacement confirmation, independent sport/mode history and statistics, and shared Players/Settings. Keep the original Squash Tap name, package and release signer.
- Support singles/doubles where applicable, two team scoring cards, shared optional opponent/partner identities and derived vector sport badges for every saved role. Removing/reopening results updates badges; bulk deletion is scoped to the selected sport.
- Preserve squash PAR-11/BO3/BO5; add badminton 21/30 and 15/21, tennis advantage/No-Ad, padel Advantage/Golden/Star, and table tennis BO3/BO5/BO7. Add nested games/sets, standard tie-breaks, optional ten-point deciding match tie-breaks and Undo across boundaries. Freeze each match's rules.
- Keep one-tap point scoring without first/second serve, let or stroke recording. Add optional server guidance, squash Game Ball/Match Ball cues and sport-specific rules/help. Do not infer missing initial service choices or automate conduct/injury rulings.
- Add B5/M4 and separate O2 team-role metadata while reading legacy B1–B4/M1–M3/O1, retaining once-only B4/O1 recovery backups, counters, timing, UUIDs and deletion markers. Preserve true rally order and unknown manual timing.
- Add CSV schema 3 with sport/mode/rules, extra participants, seven-game scores, known rally/tie-break and partial tennis retirement details. Keep schemas 1/2, reject unsupported old-watch imports, and extend phone filtering/statistics/partner records/player badges without changing relay encryption or expiry.
- Verify 294 core + 113 native UI + 43 web checks = 450; inspect native round-screen Canvas renders and synthetic phone flows. Java 17 release build, original certificate/package/API/alignment and all retained APK hashes are checked. Physical OnePlus acceptance remains separate.

## Documentation and GitHub source — 2026-11-09 (watch v0.13.0/code 14 unchanged)

- Publish the sanitized watch/site source, tests and documentation to andrew01ait/squash-tap after repository write authorization succeeds; verify all 221 imported file blobs.
- Consolidate current user, architecture, build, test and maintenance guides; add phone installation and independent versioning/source-sync documentation. Retain historical release entries and actual device evidence.
- Add documentation/link/release-identifier/parity checks and a repeatable sanitized public source export including website components and complete guides. Keep private artifact references, official hosting identity, signing keys, caches and generated APKs out of public source.
- Keep current watch/runtime behavior, update feed and every retained APK byte unchanged. Verification of this maintenance change is distinct from the recorded 400-check native release.

## Website refresh — 2026-11-07 (app v0.13.0 unchanged)

- Colour the landing-page headline’s “score.” blue while retaining red “playing.”

- Refresh the simple landing page with current scoring/manual-entry/history/opponent/CSV features, offline/no-account wording, installation/menu paths and clear QR privacy information. Remove the generic transfer launcher.
- Link only the current APK while retaining every archived APK and its metadata/checksum unchanged. Clarify the bare transfer page has no open session, and that a complete active QR link grants access until expiry.
- Verify the 35 existing web/crypto/relay checks and synthetic two-user isolation. No Android rebuild or update-feed change.

## 0.13.0 — 2026-11-07

- Reorganize the root into Current match, Results, Statistics, Opponents and Settings. Prioritize session actions, move infrequent changes/retirement into Match options, and separate current-set entry from Add past match in Results. Hide inapplicable actions after completion.
- Stage format/name choices until Create match, with explicit replacement confirmation. Clarify score review, next-set, archive-name and parent Back labels. Use light blue-arrow secondary rows, purple people, grey Settings, red removal and outlined returns.
- Bind named Back/Cancel, system Back and native Wear swipe dismissal to the same caller. Preserve result filters/page/scroll and setup drafts. A Scoreboard shortcut preserves playing/paused state. Put bulk deletion and CSV under Result options.
- Open contextual phone QR links on Results/Opponents/Import, preserve their caller and prevent late network completions from reopening menus over scoring. Keep original signing identity, B4/M3/O1/timing/CSV/encryption, power rules and every prior APK byte.
- Add 18 Android navigation regressions and two browser launch-tab cases: 265 core + 100 Android UI + 35 browser/relay = 400 checks. Physical gesture/layout/battery acceptance remains separate.

## 0.12.1 — 2026-11-07

- When saving a manually entered set, use the current watch date/time and timezone as the match start if no start is set. Keep existing starts across later entries, Undo, live scoring and reopening. Explain the fallback on confirmation; cancelling sets no time.
- Apply the rule to a current untimed v0.12/legacy match only when a new valid set is explicitly saved. Keep historical whole-match capture date-only and leave manually entered finishes unknown. Preserve B4/M3/O1, power/session rules and every prior APK byte.
- Add four core and two native UI regressions, plus update the existing continuation cases: 265 core + 82 Android UI + 33 browser/relay = 380 checks. Sign code 13 with the original app key.

## 0.12.0 — 2026-11-07

- Add MENU → Match → Enter scores with completed-set entry and independent whole-match history capture. Keep the main scoreboard clean. Use blue/red cards, winner selection, losing-score wheels, +/− and presets; dates and optional saved names also need no keyboard.
- Mix entered set totals with live scoring, preserving earlier sets. Review partial-set replacement; Continue match starts/resumes the next set. The final set saves a result. Whole-match drafts support BO3/BO5, dates, optional people, editing/removing sets and an atomic independent save that preserves the current match/session.
- Store score-only sets without a fabricated rally sequence. Confirm before whole-set Undo; unknown timing stays unknown. Add B4 with B1/B2/B3 readers/private backups and M3 for entered current sets, preserving CSV/relay/O1, original signer, references and tombstones.
- Add 23 core and 14 real Android view regressions: 261 core + 80 Android UI + 33 browser/relay = 374 checks. Retained v0.3–v0.11 APKs remain unchanged. Physical wheel/rendering acceptance is still required.

## 0.11.0 — 2026-11-07

- Fix the phone name-entry bug: additions/restorations change only the saved catalog. Match assignment requires a separate opt-in and selection; native preview ignores accidental older-page selections and highlights deliberate current/last completed changes. Existing wrong assignments can be corrected from their saved result.
- Add MENU → Match → End match with I quit / Opponent quit and confirmation. Save a clear retirement outcome with actual partial scores and timing. Count match W/L without inventing set wins/points; Undo retirement keeps every rally and waits for Resume.
- Write B3 with B1/B2 migration and private B2 backup. Keep O1 and normal M1, extend retired state as M2, support retirement CSV schema 2 while retaining schema 1 for ordinary exports. Preserve references/tombstones and original signing identity.
- Add Browse on phone under Match results, paginated/filterable expanded phone history, overall W/L across all players, per-opponent balances and recent W/L links via the existing encrypted ten-minute QR sessions. Overall scope is independent of browsing filters; selected exports identify their subset.
- Add 17 core, 12 native view and 12 browser/interchange regressions: 238 + 66 + 33 = 337 checks. Physical watch layout/retirement remains device acceptance. Retained v0.3–v0.10 APK bytes are preserved.

## 0.10.0 — 2026-11-07

- Shorten completed-match details to one date with Start/End times and duration. Keep a separate end date for overnight matches, the recorded timezone, the daily match number and every set score. Legacy missing times remain unknown; timestamps/CSV are unchanged.
- Add a separate Statistics page for overall Me match wins/losses, win rate, sets (games), points and signed balances. Derive totals from all saved completed results; imported/legacy results count, unfinished/deleted/Undo-reopened results do not. Result filters stay unchanged and do not limit these overall totals.
- Add 14 core and seven real Android UI regressions: 221 core, 54 Android UI and 21 browser/relay checks pass (296 total). B2/O1, session/power behaviour, original signer and retained v0.3–v0.9 APK bytes are preserved. Physical new-screen readability remains a watch check.

## 0.9.0 — 2026-11-04

- Give saved people and name-selection fields a distinct purple style with a person icon, left-aligned text and separate pressed/disabled colours. Menu actions remain blue, Settings grey, deletion red and Back/Cancel outlined.
- Add an optional opponent choice above BO3/BO5 during new-match setup, defaulting to No name. Choose a saved person or add/dictate a name, or skip directly to a format. Stage the choice until confirmation; cancellation keeps the old match/person/session. New matches remain idle until play.
- Add ten real Android UI regressions covering roles, optional/empty-name paths, staged selection, cancellations, adding names during setup, future results and idle policy. 207 core, 47 Android UI and 21 browser/relay tests pass. B2/O1 schemas, original signer and retained v0.3–v0.8 APK bytes are unchanged.
- Record the user’s general v0.8 success report without inferring individual CSV/dictation/battery passes.

## 0.8.0 — 2026-11-04

- Add optional opponent identities, saved-name picking, system dictation and central add/rename/remove management. Renaming updates linked history; removing names keeps past results. Existing scores/results remain unnamed without invented metadata.
- Add combined opponent/unnamed and inclusive date filters, recorded-zone date handling and filtered CSV exports. Validate CSV quoting, Unicode/BOM, semicolon files, final scores/timing and spreadsheet formula escaping. Imports preview merges, duplicates, conflicts and adjusted daily references without overwriting the active match.
- Add browser-only encrypted ten-minute transfers for CSV and opponent/current-match changes. QR fragments carry keys; the relay stores opaque ciphertext and hashed role tokens. Receive/apply are explicit, incoming changes are one-shot and stale previews are rejected. No account, phone companion app or background polling is required.
- Add the supported Worker/D1/R2 backend while preserving the public site, signing identity, package, update URL and retained v0.3–v0.7 APK bytes. Canonical assets move to public/ so framework output can be regenerated. The diagnostics QR remains independent. Expired data is inaccessible immediately and physically cleaned during later API traffic; end/apply requests delete live data.
- 207 core, 37 Android UI and 21 browser/relay checks pass, including cross-runtime crypto vectors, real local D1/R2 role/expiry/size/rate checks and native preview persistence. Physical OnePlus/phone dictation, transfer/file-picker/QR and battery checks remain pending.

## 0.7.0 — 2026-11-03

- Require an explicitly started session before keeping the scoreboard awake, attaching ambient support or running the ongoing service. Opening a fresh 0–0 draft or saved results stays idle; the first point or Start/Resume begins play. Pause, completion, task dismissal and new-match setup release the session.
- Older unfinished saved scores wait for Resume after upgrading; points, sets, Undo, timing and B2/B1 data are preserved. Undo to zero during active play retains the session; correcting a completed match stays idle until resumed. Ordinary background/ambient transitions still preserve active play.
- Guard null-intent/stale foreground-service restarts against reviving idle matches. Notification permission is requested during play. Remove launch-time update checks; manual Settings checks/downloads remain.
- Add whole-watch battery/charging snapshots on session changes and diagnostic views without polling. Bound the diagnostic QR screen flag to one minute and clear it on backgrounding. No physical battery savings are claimed.
- Add six core power-policy cases and 15 Android session/power regressions: 174 core and 25 Android tests pass. Original signer and retained v0.3–v0.6 APKs are preserved. OnePlus battery consumption and dynamic ambient-controller behaviour remain physical checks.

## 0.6.0 — 2026-11-03

- Replaced Match N result headings with local start time. Result cards/details show start and finish timestamps, total duration and the secondary daily match reference.
- Start timing on the first accepted point and finish on the match-winning point. Timing includes breaks/pauses and survives reopening, set transitions and format changes. Undo retains start and updates finish on re-completion.
- Added B2 timing storage with B1 migration/backup. Existing untimed results remain available with Not recorded; ongoing legacy matches can record a future finish without inventing start/duration.
- Added 28 timing/migration/core tests and two Android UI regressions: 168 core and 10 Android UI tests pass. Synthetic fixtures cover prior B1 formats, counters, saved scores, Undo and deletion markers.

## 0.5.0 — 2026-11-03

- Replaced the misleading Match menu root with neutral Menu and separate Match, Match results and Settings destinations. Match settings are inside Match; app updates/version/diagnostics stay inside Settings.
- Replaced inherited styling with explicit roles: blue match actions, neutral-grey Settings (including its root entry), red delete actions and confirmations, and outlined Back/Cancel controls, including the QR return control.
- Clarified diagnostic package-update/state-change exits, added page/window state to reports and pre-pause events, and kept compact traces on complete lines.
- Added 8 Robolectric tests of real Android navigation, role colours/states, deletion confirmations, result cards and format/persistence wiring. The default release builder runs these alongside the 140 core tests.
- Recorded user-confirmed v0.4 installation, diagnostic QR export and working foreground retention since v0.3. Raw diagnostic logs are not published. No changes to scoring, match storage or session policy.

## Public website — 2026-11-03

- Simplified the landing page for external users: features, current download and concise phone installation/update instructions. Removed internal debugging/chat and test-status narratives.
- Kept the separate diagnostic exporter functional with neutral support wording. App binaries, version numbers, release feed and the v0.3 retention baseline are unchanged.

## Release retention — 2026-11-03

- Made v0.3.0 the oldest retained public release at the user’s request. Removed v0.1/v0.2 APKs, their download links, inventory entries and checksums.
- Future builds enforce the minimum retained version code of 3. v0.3/v0.4 APKs, the signing identity and current update feed are unchanged; no app update is required for this website cleanup.
- Historical release notes and device-test evidence below remain as project history.

## 0.4.0 — 2026-11-03

- Split Match menu from general Settings. Results and match controls have separate menu entries; Settings holds version, updates, display/notification options and diagnostics. The scoreboard header now says MENU.
- Added a visible Match format page. BO3/BO5 can change an unfinished match in place while retaining points, set scores, match ID and Undo history. Completed formats are locked; shortening is unavailable after two set wins. No storage migration.
- Replaced grey result buttons with blue/red winner-accented cards containing date, match number and score. Match actions and scoreboard controls use blue; grey buttons are reserved for Settings and its subpages.
- Recorded the user’s v0.3 installation and navigation feedback; idle/ambient and QR tests remain pending.
- 140 shared-core tests pass, including format persistence, result consistency, final-win thresholds and deletion/reference invariants. Original signing key and historical APKs retained.

## 0.3.0 — 2026-11-03

- Blue player is Me; red is Opponent. Added BO3/BO5 matches, per-set score retention, explicit Next and cross-set Undo.
- Automatically save completed matches with local completion date and daily match number on a separate results page. Added individual/delete-all confirmations, durable deletion markers and non-reused daily numbers.
- Preserve the v0.1/v0.2 score and rally history in set 1 of BO3. Persist current match/results/counters as a validated atomic match book.
- Moved version and update controls into Settings.
- Address reported watch-face returns with a quiet ongoing match/foreground service and monochrome ambient scoreboard. Keep screen on now defaults ON for active scoreboards; added pause/resume and notification permission. Physical OnePlus behaviour remains to be tested.
- Added bounded lifecycle/session/crash/process-exit diagnostics and QR transfer to a phone-side page with TXT save/share/copy. No automatic upload.
- Added pinned AndroidX/ZXing dependencies, notices and a source-packaging helper. 127 automated core tests pass; original signer, metadata, alignment and historical APK hashes checked. No emulator/Robolectric run claimed.

## Device verification — 2026-11-03

- User confirmed phone-only installation and working operation on the OnePlus Watch 2R with Wear OS 5.
- User enabled allow-updates and successfully used the in-app updater to install v0.2.0 over v0.1.0.
- The existing score was retained. Undo-history retention and full device regression remain unconfirmed.
- Recorded results in DEVICE_TESTS.json and updated the public verification notes. APKs and version codes are unchanged.

## Distribution change — 2026-11-03

- Enabled public access to the release page, APKs and update feed at the user's explicit request.
- Documented HTTP Basic Auth as a possible future addition; current APKs do not support authenticated downloads. App binaries and versions are unchanged.

## 0.2.0 — 2026-11-03

- Update-test release: visible top version changes to v0.2.0.
- Same score format and signing identity as v0.1.0; scores and Undo history are retained by an in-place update.

## 0.1.0 — 2026-11-03

- First standalone Wear OS build with blue/red tap scoring, PAR-11/deuce handling, Undo and confirmed New game.
- Persistent score/undo history, optional keep-awake mode and visible version/menu.
- Built-in release checks, verified APK downloads, install-source setup, PackageInstaller handoff, phone fallback help and diagnostics.
- 64 local tests and verified signed APK packaging.
- Direct installation/update and score retention were subsequently confirmed by the user on the OnePlus (see device verification above). The download site was made public by explicit user approval.
