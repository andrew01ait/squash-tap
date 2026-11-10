# Testing and verification

Current watch release: **v0.15.0 / code 16**. Record the source revision and actual commands for each verification; do not present historical release results as a fresh rerun.

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
names = ('MatchFormat','ServiceOrder','ScoreState','TouchTargets','UpdatePolicy','VerifiedDownload','MatchState',
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
node --test web-test/interchange.test.mjs web-test/transfer-state.test.mjs web-test/relay.test.mjs web-test/multisport.test.mjs
```

Native/browser interchange cases execute Java fixtures; the full command requires the compiled core tests. Relay cases require Miniflare from the project's locked Wrangler dependency. Do not silently skip missing prerequisites and claim all 43 ran.

## Full native release checks

Follow [DEPLOYMENT.md](DEPLOYMENT.md) for SDK, locked runtime/UI dependencies, original signing key and verified retained APKs. The standard builder executes core/web tests, compiles real AAPT resources and runs native UI tests before D8/signing. UI tests can also be repeated once those resources/generated classes exist:

```sh
python3 scripts/test_ui.py --version 0.15.0
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
