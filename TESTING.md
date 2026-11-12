# Testing and verification

Current watch release: **v0.16.4 / code 22**. Record the source revision and actual commands for each verification; do not present historical release results as a fresh rerun.

## Google Play preparation — 2026-11-12

Fresh `python3 scripts/verify_play.py` verification passes **331 core, 191 direct-distribution native UI, 207 Play-distribution native UI and 64 web/relay tests**. The Play total includes the 191 shared API-34 cases and eight distribution/state/visual scenarios run on both API 34 and API 35. They check retained saved/current scores and names/settings/session, no website updater creation/polling, guarded legacy update/setup entry points, Play Store navigation and contextual Back, the black scoreboard background and matching black splash theme. These are two distribution runs with shared cases, not a count of distinct tests or physical watch tests.

The preparation-only 0.17.0-play.1/code 23 AAB validates with bundletool and JAR signing. A generated universal test APK has the original signer/package, minimum API 30/target API 35 and aligned ZIP entries. It excludes installer permissions, PackageInstaller implementation and InstallReceiver and has no native libraries. The AAB's certificate is also compared with the original public fingerprint. All 20 retained APK sizes/hashes and public release metadata/report remain unchanged. Pinned official tool archives and installed critical files are checksum-verified. No new app signing key is generated.

Initial preparation failures were harness/tooling issues: the managed image supplied an incomplete JDK without Java release symbols, so a verified full JDK 17 was restored; browser/native vectors required the established `build/test-classes` path; and Maven AAPT2 names its badging field `minSdkVersion`. The final full command passes after those corrections and switching the pixel-rendering assertion to native graphics mode. Native dependency debug-local warnings are nonfatal; no dependency lock changes are made.

The original APK-to-Play Store upgrade, real watch behaviour, foreground-service review, privacy/contact completion, closed testing and Google approval remain pending. Detailed gates, test instructions and review drafts are in [PLAY_RELEASE.md](PLAY_RELEASE.md). Candidate artifacts stay outside public APK feeds; source publication does not launch Google Play distribution.

## Recorded release evidence

| Suite | v0.13 recorded passing checks | Coverage |
| --- | ---: | --- |
| Shared core / JUnit | 265 | PAR-11/deuce/BO3/BO5, Undo, persistence/migrations/recovery, timing, manual totals, retirement, counters/tombstones, people, filters/statistics, CSV, crypto and update/download policy. |
| Native UI / Robolectric API 34 | 100 | Real MainActivity/dialog/button callbacks, compiled AAPT resources, contextual Back/swipe wiring, styles/targets, session/window flags, manual controls and late transfer completion guards. |
| Browser/relay | 35 | 13 interchange/crypto, 12 transfer-state and 10 Miniflare D1/R2 relay checks; native/browser vectors, CSV and capability/isolation/expiry/storage behavior. |
| Total | 400 | Automated release evidence; not a physical Wear OS emulator/watch battery test. |

The preserved combined report is `public/test-results.txt`. APK metadata/signatures/alignment and immutable retained checksums were also verified by the release builder. [DEVICE_TESTS.json](DEVICE_TESTS.json) records actual user reports separately; raw user diagnostics are not published.

## Checks without the private signing key

Documentation integrity and the independent phone-state suite require only Python 3 and Node.js >=22.13:

```sh
python3 scripts/check_docs.py
node --test web-test/transfer-state.test.mjs
```

With JUnit/Hamcrest prepared by `scripts/bootstrap_android.py`, the shared-core tests can run before any signing step:

```sh
python3 - <<'PY'
from pathlib import Path
import os, subprocess
root = Path.cwd()
folder = root / 'android/src/at/andrew/squashtap'
names = ('HeadToHead','ScorePalette','BackupArchive','MatchPresets','MatchReminders','PickleballGame','MatchFormat','ServiceOrder','ScoreState','TouchTargets','UpdatePolicy','VerifiedDownload','MatchState',
         'MatchResult','MatchStatistics','ManualMatchEntry','MatchBook','MatchDisplayPolicy',
         'DiagnosticTransfer','OpponentDirectory','ResultFilter','MatchCsv','TransferCrypto')
tests = sorted((root / 'android/test').rglob('*Test.java'))
classes = root / 'build/test-classes'
classes.mkdir(parents=True, exist_ok=True)
cp = os.pathsep.join(str(root / '.local/android-sdk' / p)
                     for p in ('junit.jar', 'hamcrest-core.jar'))
subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-classpath',cp,
                '-d',str(classes),*[str(folder / (n + '.java')) for n in names],
                *map(str, tests)], check=True)
subprocess.run(['java','-cp',str(classes) + os.pathsep + cp,'org.junit.runner.JUnitCore',
                *['at.andrew.squashtap.' + p.stem for p in tests]], check=True)
PY
```

After core classes exist, install locked Node dependencies and run the complete web suite:

```sh
node --test web-test/interchange.test.mjs web-test/transfer-state.test.mjs web-test/relay.test.mjs web-test/multisport.test.mjs web-test/backup.test.mjs web-test/live.test.mjs
```

Native/browser interchange cases execute Java fixtures; the full command requires the compiled core tests. Relay cases require Miniflare from the project's locked Wrangler dependency. Do not silently skip missing prerequisites and claim all 56 ran.

## Full native release checks

Follow [DEPLOYMENT.md](DEPLOYMENT.md) for SDK, locked runtime/UI dependencies, original signing key and verified retained APKs. The standard builder executes core/web tests, compiles real AAPT resources and runs native UI tests before D8/signing. UI tests can also be repeated once those resources/generated classes exist:

```sh
python3 scripts/test_ui.py --version 0.16.4
```

Robolectric executes API 34 and uses its configured API 35 framework for resource parsing. Test tooling stays under ignored `.local/`; it is not bundled in the APK. A fresh export does not contain the maintainer's signing key, SDK or prior unsigned build, so the complete release pipeline is not a key-free out-of-box build.

Before distributing any new APK, verify original certificate/package/version/SDK/watch metadata, alignment, download size/SHA-256 and all retained APK hashes. Inspect the updated update feed and report. B1/B2/B3 migration fixtures, manual/live mixtures, retirement, Undo, deletion and optional-name assignment must remain covered.

## Documentation/source synchronization checks

`check_docs.py` requires the complete guide set, validates local Markdown links, verifies current release identifiers and can compare every current guide with an exported copy:

```sh
python3 scripts/package_source.py --github-dir /absolute/path/to/empty-public-source
python3 scripts/check_docs.py --compare /absolute/path/to/empty-public-source
```

Verify public export paths, sanitized hosting/signing metadata and absence of credentials, private artifact references, real user exports, generated APKs and Git/build/cache directories. Verify runtime source bytes and existing release artifacts are unchanged for docs-only work. After publishing, compare the exact GitHub tree/blob hashes and check the internal pushed revision/deployment status. This is source verification, not new native/physical acceptance.

## Physical OnePlus acceptance

- Scoring/deuce/Undo/BO3/BO5, NEXT and completed result storage, including optional opponents and retained history across updates.
- Real swipe recognition, contextual Back, round-screen readability, 48dp target comfort, wheels and manually entered sets/past matches.
- No silent name reassignment after phone catalog edits; correct filtered-page/scroll return and confirmed single/bulk deletion.
- Retirement winner versus actual sets/points, Undo retirement and resumed play; missing/manual/overnight times shown honestly.
- Optical QR scan, browser file picking/download/sharing, explicit receive/preview/apply/cancel, schema/conflict/expiry behavior and updater installation.
- Active-match availability/ambient transitions and idle sleep after Pause/completion/task dismissal. Measure battery on the actual device; event snapshots alone do not attribute energy to this app.

Initial phone-only installation, v0.1→v0.2 update/score retention, later version installations, working foreground retention since v0.3 and a v0.4 diagnostic QR export were user-reported. They do not automatically validate a future app/firmware release. Ordinary activity pauses and package-install exits are not evidence of a crash.

## Documentation/publication verification — 2026-11-09

The GitHub import ran all 12 dependency-free phone-state checks, reviewed public-source metadata and verified 221 uploaded blob hashes against the prepared source. This documentation refresh passed checks for all 10 guides and 51 local links, exact guide/export parity, a complete 224-file sanitized source ZIP, all 12 retained APK hashes and all 12 phone-state cases. Application/runtime source, release metadata and historical device reports were verified unchanged. Export guards reject existing/checkout-local destinations, and repeated packaging is deterministic. The local frontend build could not run because its Vinext dependency cache is absent; Site publication therefore requires the supported remote build of the verified pushed source. The historical 400-case native release was not rerun and no physical watch/battery acceptance is claimed. The app remains v0.13.0/code 14.

## Multisport release verification — 2026-11-10

The historical v0.14.0/code 15 release suite contained 294 core checks, 113 native UI checks and 43 web/crypto/relay checks (450 total). New tests cover five formats, old/new badminton and padel presets, nested game/set and deciding tie-break Undo, seven-game table tennis, service rotation including the deciding-game receiving switch, B4/B5/O1/O2 compatibility, team roles, derived badges after deletion/reopening, partial tennis retirement scores and native/browser CSV-3 parity.

Native Canvas renders inspect all five scoreboards, the sport selector and player badges on a round 233dp viewport. The phone preview uses synthetic data and checks separate sport history, scoped statistics, unknown point totals and shared player sport badges. Native UI tests preserve timing, manual entry, Back/swipe, session/power and transfer guards. These remain automated/desktop checks: installation, optical QR scanning, physical OnePlus touch/ambient behavior and battery acceptance require the user's watch. No new physical-device report is added.

Build using the documented Java 17 toolchain. The environment's initial Java 21 compiler passed model/UI checks but generated an anonymous-class form that the pinned Android 34 D8 could not process. The official package uses a fresh Java 17 build and the original signer; no Android dependency lock or identity check is weakened.

## Navigation release verification — 2026-11-10

v0.15.0/code 16 passes **299 core + 130 native UI + 43 web/relay checks = 472** using the documented Java 17 release builder. The added checks cover discard without a result/reference, preservation of shared names/history/counters, all sport formats, finished-match protection, active/paused/prepared sport locks across restart and session removal, stale selection/create/discard callbacks, selected rule/mode/decider lists, picker cancellation/swipe and shared Players/Settings returns.

Native Canvas inspection covers the five sport scoreboards, coloured sport menu, shared player badges, setup, scoring list, current controls, discard confirmation and locked sport selector at a round 233dp viewport. Selection options precede help text, confirmations omit the Scoreboard shortcut, and setup/options/settings/serving preserve scroll on picker return. The full builder also checks the original certificate/package/API/watch metadata, ZIP alignment and all retained release hashes. A local website build passes. Physical OnePlus installation, touch/swipe, ambient behavior and battery acceptance remain separate; no new device report is claimed.

## Serving and landing release — 2026-11-10

v0.15.1/code 17 passes 299 core + 135 native UI + 43 web/relay checks = 477. Five additional native rendering regressions cover empty/long names at 180/200/233/260dp equivalents, all four doubles service slots, unknown/completed state, side changes with Undo and ambient service text. Native Canvas previews show the actual synthetic app views; they do not establish physical-device acceptance. Original signing identity, metadata/alignment and all 15 retained APK hashes are verified. The website refresh is checked separately for preview switching, links, images and responsive layout.

Landing-page browser QA verified five radio/CSS previews and keyboard arrow selection, all eight PNGs loaded, installation anchors and retirement/discard help. Desktop width 1363px and narrow CSS viewports 454px/340px have no horizontal overflow. Narrow checks use browser zoom/reflow, not a physical phone.

## Keep Score verification — 2026-11-11

v0.16.0/code 18 uses the Java 17 release pipeline. The suite is 325 core + 170 native UI + 56 web/relay cases (551 total). Added coverage includes traditional pickleball non-point rallies/server two/courts, singles/doubles and 2025/2026 finishing differences, all preset/CSV/book combinations, backup validation/checksum/Undo/presets/name/settings, envelope-version binding and native↔browser backup interoperability, reminders and high-contrast palette checks. Native UI covers own name, favourite/rematch behavior, backup cancel/atomic apply/stale-setting rejection/recovery access, live coalescing/offline reconnect/stale-session guards, tile/complication data and tap actions, colour list/cancel/ambient and system brightness delegation.

API-34 native Canvas renders cover six sport scoreboards and nine profiles: round 192/216/233/240dp, square 180/240dp, rectangular 201×238dp, and round 192/240dp with 130% system text. Scoreboard, current menu, menu bottom and Players are rendered for each; long player names retain an independent serving cue. Three palette renders inspect Classic/Neon Lime/Sunlight. These are actual native view renders with synthetic data, not installed watch screenshots.

The live API suite checks audience read-only roles, encrypted content, no listing/CORS/cache, ordered/parallel revisions, expiry/revocation, field/body bounds and hashed-IP rate limits. Browser QA uses only local synthetic fixtures, separately from production sessions. Physical OnePlus sunlight/brightness/reflection, vibration feel, tile/complication system rendering, touch/swipe/ambient, optical QR, phone file sharing and watch-network reconnect/battery acceptance remain to be performed; DEVICE_TESTS.json is unchanged.

The audience page adapts to portrait phones and short landscape screens. Portrait keeps two large aligned cards; landscape places match details and controls alongside them. Full team names wrap, serving has its own badge, and viewers independently choose Classic/Neon Lime/Sunlight. Browser QA covers actual 393×852 and 852×393 CSS viewports with synthetic doubles names, aligned scores, all controls reachable and no horizontal overflow. These are browser layout checks, not physical phone acceptance.

## Navigation and live corrections — v0.16.1, 2026-11-11

The patch suite includes 325 core + 185 native UI + 58 web/relay cases (568). New native checks cover persistent Settings ticks, selected-option taps, contextual Back/swipe, auto QR readiness/failure/background/late response behavior, bounded QR retention, discard confirmation versus cancellation, anonymous terminal payloads, offline reconnect and starting another broadcast. Web checks reject names/scores in discard notices and verify replacement ciphertext with audience read-only access. Old release evidence remains historical. Physical watch and live-network acceptance remain pending.

## Shared menu branding — v0.16.2, 2026-11-11

The full release suite passes 325 core + 185 native UI + 58 web/relay cases (568). The existing shared-navigation regression checks Keep Score on the sport selector, Players, Settings and a nested settings selector, absence of ALL SPORTS identity text, and return to the sport selector. Existing sport-context tests retain the actual sport name/icon/accent. Native Canvas renders cover updated shared headers and sport menus. Original signer/package/SDK/alignment, immutable retained APK hashes, documentation/export parity and a matching local website build are verified. Physical watch acceptance remains pending.

## Audience history and final results — v0.16.3, 2026-11-11

331 core + 190 native UI + 64 web/relay checks pass (585). Added coverage includes stable/archived/renamed identities, exact doubles teams with swapped opposing roles, unknown identity exclusion, backfilled result dates, current/deleted result exclusion, retirement winners, native summary privacy, stable completion metadata, Undo and queued final retries after rematch/task dismissal. API tests cover fixed ten-minute completion deadlines, the live cutoff, delayed offline completion, legacy rows, invalid markers, read-only roles and revision ordering. The alphabetical selector and browser/native fixture tests verify all six sports without positional sport assumptions.

Native renders inspect the alphabetical selector and badges. Browser preview verifies portrait 393×852 and landscape 852×393 frames, aligned current/final scores, long doubles names, head-to-head/last meeting, winner/score breakdown, remaining availability and labelled share/copy icons without horizontal overflow. Browser-driven transition controls could not be verified because the test iframe hit the browser URL policy; the API/native transition regressions pass. Physical OnePlus acceptance remains pending. Original signer/package/SDK/alignment, all retained hashes, documentation/export parity and the local site build are checked separately.

## Result filter reset — v0.16.4, 2026-11-11

331 core + 191 native UI + 64 web/relay checks pass (586). The native regression combines mode, opponent and date filters, resets from page two, checks page one/top despite an earlier remembered scroll, retains Tennis, and verifies byte-identical match/name books and the active session. It also checks the first filter action and recovery from an empty result list. Browser QA applies Doubles, a named player and both dates, then verifies All modes/All players/empty dates, the same Tennis scope, restored results and the success message. Documentation/export parity, original signer/package/SDK/alignment, all retained APK hashes and the website build are verified. No new physical-watch acceptance is claimed.

## Website explanation audit — 2026-11-11

The live landing page/update feed report v0.16.4/code 22; APK, source ZIP and checksum links return HTTP 200. The audit identified missing reset instructions and an outdated match-only/eight-hour privacy sentence. Updated feature/FAQ/privacy copy now matches the existing implementation. Documentation and sanitized-export parity, referenced local assets, current source/website build and unchanged release metadata/20 APK hashes are checked. This is a website-copy update: the recorded 586 native/web release checks and physical-device evidence remain unchanged and are not claimed as a fresh native rerun.

## Dedicated live streaming feature section — 2026-11-11

Browser QA inspects the new setup steps and illustrative scoreboard in 393×852 and 852×393 frames. A 320×852 frame checks the compact navigation and score cards; all three page widths have matching client/scroll widths without horizontal overflow. The two example score cards align and remain readable, with the sample clearly labelled. The development-only layout fixture uses the public landing HTML at `/index.html`; a cached service worker from an unrelated local preview served stale content at `/`, so that cached root was not used as feature evidence.

Validate unique section/preview IDs, navigation/hero anchor targets, all referenced local files, matching HTML/framework descriptions, documentation/export parity, production fixture exclusion and the website build. Recheck all 20 retained APK hashes/sizes and byte-identical release metadata. This update changes website presentation and documentation only; the v0.16.4 release's recorded 586 checks are not a fresh native rerun, and physical-watch acceptance remains unchanged.
