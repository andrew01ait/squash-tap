# Build, install and update

## Rebuild

Required on the build machine only:

- Java 17 with `jdk.compiler` and `keytool`.
- Android SDK Build Tools 34.0.0 and platform 34.
- JUnit 4.13.2 and Hamcrest Core 1.3 jars.
- Python 3.

```bash
python3 scripts/build.py --sdk /path/to/android-sdk --junit /path/to/junit-4.13.2.jar --hamcrest /path/to/hamcrest-core-1.3.jar
```

The builder also accepts the official extracted SDK archive layout `android-14/` (build tools) and `android-34/` (platform). In the original build session it is `/tmp/wear-probe/sdk`.

The build runs 265 shared-core JUnit tests and 100 Android navigation/session/power tests, prepares pinned runtime dependencies, builds only the new v0.13.0 APK, verifies its original signing identity and ZIP alignment, checks package/version/watch metadata, and writes the release feed/checksums to `public/`. Public history starts at v0.3.0 (version code 3), as requested by the user. `scripts/release_retention.py` removes older APKs and optional signature sidecars from distribution and refreshes builds.json/checksums; the builder invokes it automatically. Retained APKs are immutable: their recorded SHA-256 hashes must match before building; do not rebuild them from new source.

Runtime dependencies are pinned in `android-dependencies.json` (AndroidX ambient/ongoing activity and transitive dependencies, plus ZXing QR generation). `scripts/androidx_deps.py` downloads from official Google Maven/Maven Central into ignored `.local/androidx/`, verifies each locked artifact and extracts AAR classes/resources. Normal builds use the lock; run `python3 scripts/androidx_deps.py` only when deliberately resolving changed dependencies, then review the new lock. AAPT2 generates R classes for app/library packages before Java/D8 compilation. Before D8/signing, `scripts/test_ui.py` runs Robolectric API-34 tests against the real activity and the compiled resources. Its Maven/Robolectric/framework dependencies are prepared automatically in ignored `.local/`; they are build-only and require no tools on the user’s PC. Third-party notices are bundled under APK assets.

After updating docs and the public page, regenerate the source download:

```bash
python3 scripts/package_source.py
```

The ZIP includes source, tests, build scripts, dependency lock, documentation, notices and website HTML/hosting configuration. It excludes keys, credentials, SDKs, caches, Git metadata, build outputs and APKs.

## Signing continuity

The first build creates `.local/signing/squash-tap.p12` and `.local/signing/key.password`. They are ignored by Git and must never be placed in `public/` or any public download. The same key is mandatory for all future in-place updates. A private signing backup is saved separately; retain its recorded Library identity in project maintenance records. Restore the signing files before a rebuild in a fresh workspace. Do not generate a new key for an existing installation.

Current release certificate SHA-256:
`57f2fb958db2b908806a01d1ca03dc60f3c5faed06fbe6d848a7d0829ff0abab`.

## Phone-only initial installation

This procedure succeeded on the user's OnePlus Watch 2R with Wear OS 5, using an Android phone's hotspot. The initial APK was v0.1.0; the in-app update to v0.2.0 and retention of the existing score were subsequently confirmed. No PC was used.

1. Install [Wear Installer 2](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller2) on the Android phone.
2. Download **SquashTap-0.13.0.apk** to the phone’s Downloads folder. v0.3.0 is the oldest retained release; v0.1/v0.2 are no longer distributed. The scorekeeper APK runs on the watch; Wear Installer 2 is the helper installed on the phone.
3. Connect the watch to the phone's hotspot, or connect phone and watch to the same Wi-Fi. On the watch open **Settings / Einstellungen**, then **Wi-Fi / WLAN**, possibly under **Connection / Verbindung**, and select/add the network. Wait for **Connected / Verbunden**.
4. Enable Developer options on the watch: **Settings → Other → About watch → Other version info → Build number**, then tap Build number seven times. See the German labels below. Return to Developer options and enable **ADB debugging** and **Wireless debugging**.
5. Tap the **Wireless debugging row/text** to open its details. Enter the **watch's IP address** from that screen in Wear Installer 2. Choose **Pair with watch → Enable** on the phone, then **Pair new device** on the watch. Keep the watch's pairing screen open. In the phone's single field enter **pairing code, one space, pairing port**, then **Done**. Wait for **Pairing successful**.
6. Go back to the main Wireless debugging screen on the watch. Enter its current **IP address:connection port** on the Wear Installer 2 home screen. This connection port differs from the pairing port and can change. Use the values displayed on the watch, not the phone's hotspot/mobile-network IP address.
7. Choose **Custom APK → Downloads**, select the APK and install. Open **Squash Tap** from the watch's app list and check **MENU → Settings → v0.13.0**. The first point or MENU → Current match → Start match activates play; allow notifications when prompted for the ongoing match shortcut.
8. Turn wireless debugging off when it is not needed.

Detailed installer instructions: https://freepoc.org/wear-installer-2-help-page/

### German OnePlus menu guide

Translations and grouping can vary with firmware. Start in the normal watch Settings app, rather than inside the developer menu, when configuring Wi-Fi.

| English label | German label to look for |
| --- | --- |
| Settings | Einstellungen |
| Other | Sonstiges / Andere |
| About watch | Über die Uhr / Info zur Uhr |
| Other version info | Weitere Versionsinformationen |
| Build number | Build-Nummer |
| Developer options | Entwickleroptionen |
| Wireless debugging | Drahtloses Debugging / Debugging über WLAN |
| Pair new device | Neues Gerät koppeln / Gerät mit Kopplungscode koppeln |
| IP address and port | IP-Adresse und Port |

### Pairing versus connecting

| Stage | Values to use | Where to find them |
| --- | --- | --- |
| Pair once | Six-digit pairing code, a space, then pairing port | Watch → Wireless debugging → Pair new device |
| Connect/install | Watch IP address, colon, then connection port | Watch → main Wireless debugging screen |

Pairing is authorization; connecting uses a separate port. Do not reuse the pairing port in the installer's normal address field. If the address or connection port changes, refresh the installer field from the watch's current screen.

## Direct update setup and proof

The release page is https://squash-tap-andrew.endruuu.chatgpt.site . The application fetches `/release.properties` and then a same-origin HTTPS APK.

The user explicitly authorized public access on 3 November 2026 and chose to keep the distribution entirely public. Keep the page, APKs and release feed accessible without ChatGPT sign-in, HTTP Basic Auth, tokens or a password. Do not add authentication or change the audience unless explicitly requested. A private sign-in response is reported as an update error; it is never treated as an APK.

In v0.4 and later open **MENU → Settings → Check for updates**. In v0.3 open **Settings → Check for updates**. Choose **Set up direct updates** or **Allow app updates** if needed. Enable the permission on the system screen, then return to Squash Tap and select **Check for updates**. The user successfully enabled this permission on the OnePlus and completed the update. Initial sideloading is the only part that needs the phone installer; subsequent in-app updates need internet access and install-source permission, with no active ADB connection required.

If another firmware hides the permission screen, a paired phone ADB utility may permit this one-time fallback command:

```text
appops set at.andrew.squashtap REQUEST_INSTALL_PACKAGES allow
```

Use the paired phone setup; no PC is required. This fallback was **not needed or tested in the confirmed run**. Its effectiveness on other firmware is unconfirmed. If unsupported, use phone installation instead.

### Update an existing watch to v0.12

1. Keep the installed app/data. Connect the watch to internet access.
2. Open **MENU → Settings → Check for updates**.
3. Confirm **v0.13.0** is offered, then **Download & update**. Accept Android's installer if requested.
4. Reopen Squash Tap and verify **MENU → Settings → v0.13.0**. A saved unfinished match from an earlier version waits for **MENU → Current match → Resume match**. New drafts remain idle until the first point or Start match.
5. Verify your active score, set history and saved results. Open **MENU → Current match → Match options → Match format** to select BO3/BO5 without clearing an unfinished match. Finished matches keep their format; shortening is unavailable after two set wins. **NEW** still starts a fresh match with an explicit format choice.
6. Find saved results under **MENU → Results**. Check blue/red result cards, blue match controls, neutral-grey Settings actions, red delete actions/confirmations and outlined Back/Cancel controls. Settings now contains only general app controls. Diagnostics is **MENU → Settings → Diagnostics**.

The user confirmed the v0.1 → v0.2 update/score retention and later installed v0.3. The user subsequently confirmed v0.4 installation and QR export, and reports foreground retention working since v0.3. The user approved v0.5’s cleaner design. v0.6 timing, detailed ambient transitions and battery use remain physical checks. To update manually, download the current APK to the phone and install over the existing app with Wear Installer 2. Do not uninstall between versions.

v0.7 checks for updates only when selected in Settings. It has no launch-time or periodic update check. The update/download verification and phone fallback are unchanged.

### Match availability and diagnostic trial

The ongoing match indicator requires a started session and notification permission. Keep screen on defaults ON but only applies during active play. **MENU → Current match → Pause match** ends the session, and Resume restarts it without resetting points/timing. New-match setup, paused/completed matches and old saved scores awaiting Resume have no ongoing service or ambient observer. Test active foreground retention past the previous timeout interval, lower/wake the wrist, then repeat with Keep screen on OFF to test ambient (with watch AOD enabled if a visible ambient display is desired). Do not confuse normal idle return to the watch face with loss of an active match.

For a battery trial, pause or finish first, turn off wireless debugging, and note whole-watch battery level/time. Leave the app idle or return to the watch face for a useful interval (for example 30–60 minutes); repeat under comparable brightness/AOD/network conditions with the app unused. Compare with active play separately. Diagnostics includes active-session state and on-demand battery/charging readings plus bounded session-transition snapshots. These whole-watch readings cannot isolate app consumption. No energy reduction has been measured on the physical OnePlus yet. The QR's separate screen flag expires after one minute or immediately on backgrounding.

If the app returns to the watch face, reopen promptly and use **MENU → Settings → Diagnostics → Send to phone**. Scan the watch QR code, open its HTTPS page, save the TXT and share it with support. No paired ADB connection or PC is needed for export. The report is compressed into a URL fragment and decoded locally in the phone browser, with no automatic server upload. Copy text and a photo of the watch diagnostics are fallback options.

## Troubleshooting

| Symptom | Next check |
| --- | --- |
| Developer options are missing | On OnePlus, look under Other / Sonstiges / Andere and tap Build number seven times. |
| No watch IP address is shown | Connect the watch to Wi-Fi or the phone hotspot, enable Wireless debugging and open its detail page. |
| Cannot find the pairing code | Tap the Wireless debugging text/row to open details, then Pair new device; scroll if needed. |
| Pairing fails | Keep the watch's code screen open and enter the current code and pairing port separated by one space. |
| Pairing succeeded, installation cannot connect | Use the connection port from the main debugging screen, not the pairing port. Refresh the current watch IP/port. |
| Permission was granted but the update action is missing | Return to the app and tap Check for updates, or close/reopen its Updates panel. |
| In-app download or install fails | Note the status in Updates/Diagnostics. Retry the release check or install the new APK over the current app from the phone. |
| Match returns to the watch face | Reopen promptly; export Diagnostics via QR. Include time/idle interval and whether Keep screen on was ON. Check notification permission and that the match is not paused. |
| Saved match does not stay on screen after updating | Open MENU → Current match → Resume match. v0.7 keeps older saved scores idle until explicitly resumed; opening history alone must not start a session. |
| Match is unfinished but you have stopped playing | Use MENU → Current match → Pause match. This retains the score/timing and releases the ongoing service, ambient support and screen flag. |
| Diagnostic QR will not scan | Hold the watch/phone still, avoid glare and move the phone slowly to focus. Send a clear photo of the QR or diagnostics as a fallback. |

## Preparing a future app release

1. Restore the existing signing key if needed; retain the package ID and schema or implement/test a migration.
2. Increase the version code monotonically. Update the version list and generated release metadata in `scripts/build.py` for the new release; retain APKs from v0.3.0 onward byte-for-byte and update third-party notices if dependencies change.
3. Run the shared-core tests and build/signature checks. Confirm the release feed identifies the newest APK, its actual size, checksum and supported SDK.
4. Update README, deployment, architecture, flow, changelog, testing and AGENTS records. Update `public/index.html` and run `scripts/package_source.py`. Device reports are historical evidence for their recorded version pair; they do not automatically validate new builds.
5. Publish the matching source revision, Worker and public assets while preserving the public audience.
6. On the watch, check for the new version, update in place, reopen and verify both the version and saved score.

## Site publication

The Site identity is in `.openai/hosting.json`. Static output is `public/`. Source and release APKs are retained in its Git source repository; signing keys are excluded. Use the Sites source workflow, push the exact tested source, then save and deploy that source revision. This existing Site's audience is public and must be preserved on later deployments.

D1 and R2 hold only short-lived encrypted transfer state and hashed capabilities/rate buckets. There is no account, analytics, permanent score storage or diagnostic-upload endpoint. `/diagnostics.html` is a client-side decoder only. `release.properties` should have `Cache-Control: no-store`; APKs use versioned filenames.

Keep the public landing page focused on app features, downloads and simple end-user installation/update instructions. Do not add personal chat/debugging workflows or internal test reports to its main content. Keep the diagnostic decoder available at its existing URL for the watch QR flow; its export wording is neutral.

Android UI checks can also be run separately after the resources exist: `python3 scripts/test_ui.py --version 0.13.0`. The report is `build/0.13.0/ui-test-results.txt` and is included in the combined public test-results file; keep test details off the public landing page.

v0.6 backs up the old B1 match book once to `match_book_v1_backup` and writes B2. Check existing results, score/Undo, daily references and deletions after updating. Missing legacy start/finish times stay Not recorded; an already-running legacy match has no recoverable start/duration. Use a fresh match to validate first-point start, last-point finish and duration, including reopening and Undo.

B2 was written by v0.6–v0.10; v0.11 wrote B3; v0.12 writes B4 while reading B1/B2/B3. Earlier APKs do not understand B4. Downgrading after migration is unsupported. Preserve the private preference backups for recovery and keep the app installed during normal updates.

## v0.8 backend and release build

Node >=22.13 and the locked pnpm dependency tree are build-only tools for the relay/site and its tests. Restore/install with the Sites dependency helper or the selected execution profile. Python scripts/bootstrap_android.py restores Build Tools/platform 34 and JUnit/Hamcrest from official repositories, verifying their published checksums. Restore the original private signing backup separately, then run:

```bash
python3 scripts/bootstrap_android.py
python3 scripts/build.py --sdk .local/android-sdk --junit .local/android-sdk/junit.jar --hamcrest .local/android-sdk/hamcrest-core.jar
python3 scripts/package_source.py
```

The builder runs 265 core tests, 100 Android UI tests and 35 browser/relay tests before signing. Browser/native encryption vectors execute across both runtimes. Canonical assets/feed/APKs live in public/ so a frontend build cannot erase retained releases. After all source/docs/assets changes, regenerate the ZIP and run the Sites build entrypoint. The generated deployment includes public asset paths unchanged and the relay Worker. Keep the existing Site project/audience; do not create a new Site or introduce sign-in.

.openai/hosting.json declares D1 DB and R2 BUCKET. The initial Drizzle migration creates transfers and transfer_rates. Generate/append future schema changes with npm run db:generate, preserving applied SQL/snapshots/journal entries. For local preview, apply pending SQL through Wrangler using the generated dist/server/wrangler.json and .wrangler/state. Sites applies hosted migrations during publication. Never create tables in a request handler.

Relay keys: /api/transfer POST creates a snapshot; /api/transfer/id/outgoing GET is phone-only; /api/transfer/id/incoming POST is phone-only and one-shot; its GET is watch-only; DELETE /api/transfer/id is watch-only. Capabilities are sent over HTTPS and hashed at rest; encryption keys never enter production requests. Do not inspect/log real payloads or tokens. Health can be checked by an unauthorized read returning 401 or an unknown session returning 404; use synthetic data for complete transfer testing.

Access expiry is ten minutes, checked on every request. Ending or successful watch confirmation deletes live objects/session metadata. Unused expired objects and metadata are purged in bounded batches during later transfer API requests; there is no independent scheduled physical-erasure deadline. Failures to send the final delete leave ciphertext inaccessible after expiry. The service is not a backup. No periodic watch requests or foreground transfer service are added.

v0.7 active-session state survives the update; v0.6 and earlier unfinished scores without an explicit session ID require Resume. O1 opponent metadata is added without altering B2 scoring storage or old missing times. Speech uses a system recognizer and remains optional; the phone browser is the keyboard alternative. The phone HTTPS page requires Web Crypto and Compression/DecompressionStream. An HTTP-only managed preview cannot decrypt; do not weaken HTTPS requirements to make it do so.

Phone walkthrough: watch MENU → Results → Options → View results on phone → Show phone QR; scan/open; download CSV or prepare opponent/import changes; Send changes; watch Receive changes → review → Apply changes. For a filtered watch export use Results → Filters → Export selected to phone. End an existing session before changing the exported selection. A process restart loses the in-memory transfer and needs a new QR. The old diagnostics page and updater keep their fixed public URLs.

## v0.12 upgrade and smoke check

Update in place through MENU → Settings → Check for updates and confirm v0.13.0. No new runtime dependencies are required. B1/B2/B3 match data migrates to B4 with private recovery backups; O1 and the original package/signer are preserved. Person rows/fields are purple with an icon, while menu actions, Settings, Delete and Back retain their established roles.

Open NEW: optionally choose a saved opponent or Add opponent, then choose BO3/BO5. Leaving No name untouched must start an unnamed match. Choose a different name during setup and cancel once: the old score/opponent/session must remain. Confirm replacement once and check the new score is 0–0, the selected name appears and the session stays idle until playing. Complete a disposable match to confirm its result/filter/CSV use that person. Prior results and old opponent assignments are retained. The user reported v0.8 worked generally; this is not a claim that every CSV/file-picker, dictation or battery scenario was individually verified.

## v0.10 timing and statistics acceptance

After updating, open a completed result: one recorded local date, Start/End times, duration, daily match number and all set scores must remain visible. An overnight match shows the end date as well. Legacy unknown times remain Not recorded. The recorded match timezone controls the displayed day, including after travelling. CSV timestamps and B2/O1 schemas are unchanged.

MENU → Statistics shows all saved completed matches from Me’s perspective: match wins/losses and win rate, sets (games) won/lost, points for/against, totals and signed balances. Results filters do not apply. Imported and legacy results count; unfinished/deleted results do not. Delete a disposable result and reopen Statistics to verify recalculation. Undo/re-completion of the current result must remove/re-add it once without stored aggregate counters. Opening Statistics must not activate an idle match or make network requests.

The release adds 14 core and seven real Android view regressions: 221 core + 54 Android UI + 21 browser/relay = 296 automated checks. Physical OnePlus compact text/layout remains a device acceptance check.

## v0.11 migration, retirement and phone-page acceptance

v0.11 migrates match books to B3 with B1/B2 readers and a once-only private B2 backup, while preserving O1 opponent identities, rallies, timing, daily counters and deletion markers. M1 remains the normal active encoding; M2 records a retired player without fabricated rallies. Restore the original signing key and update in place. Downgrading to v0.10 or earlier after B3 migration is unsupported. Schema-1 CSV remains byte-compatible for all-normal exports; mixed/retired exports use schema 2 with retired_player. Older watches receive a clear import-upgrade message for retired files.

On the watch, MENU → Current match → Match options → End match early offers I quit / Opponent quit plus confirmation. Verify both cancellations, win/loss result, actual partial score, duration and statistics. Completion must release the ongoing service and screen flag. Undo retirement restores all rallies and waits for Resume; deletion must not revive a result. This control must not appear on the main scoreboard.

MENU → Results → Options → View results on phone sends all saved results through the same encrypted temporary QR session. Verify phone opponent/date filters, pagination, expandable result detail, Statistics across all players, per-opponent balances and recent W/L links. Overall scope is independent of browsing filters and a selected export must identify its limited scope. Browsing is local after the encrypted snapshot loads; no polling/account is added. Ten-minute access/cleanup semantics are unchanged.

Reproduce the name bug by adding several new people with the last match current: the opponent selection must stay unchanged. Only enabling Change a match opponent and selecting a person may request reassignment. Watch preview identifies the affected current/last completed match. Old accidental assignments require a deliberate correction from the saved result; never guess or bulk-clear existing identities.

Expected release suite: 238 core + 66 Android UI + 33 browser/relay/state = 337 checks. New native view layout, watch QR scanning and retirement on the physical OnePlus remain device acceptance checks.

## v0.12 keyboard-free entry acceptance

Install in place with the original signer. B1/B2/B3 are read and written as B4; a B3 string is privately retained once under match_book_v3_backup without overwriting earlier backups. Only current matches containing manually entered set totals need M3; those totals use score-only encoding, with no synthetic rally order. Completed results and CSV/relay formats are unchanged. Downgrading after B4 migration is unsupported.

On the round watch open MENU → Current match → Enter set score. Verify blue/red winner choices, large score cards, losing-score wheel, +/− and presets, including 11–9 → 12–10. No manual entry screen should invoke the keyboard. Date wheels block descendant editing; saved opponent picking uses purple person controls. Back/Cancel stay outlined; Remove last set is red.

Enter one completed set, then Score next set live to move to the next set and start/resume play. Also try Enter next set score for multiple manually captured sets; the final set should save the match and release its service/screen flag. A fresh manual entry stays idle until Score next set live/Resume; an already active session stays active between sets. Confirm before replacing a partially tracked current set or undoing an entire entered set. Point order and a manually entered finish remain unknown. From v0.12.1, if the match start is absent, Save set score records the current date/time and watch timezone. Existing starts are preserved. Cancel must leave an unset start unchanged. Add past match remains a historical date-only entry.

Record a whole BO3/BO5 match with an optional saved opponent and date; use wheels or Today/Yesterday, edit a card and remove a draft set. Save once and verify date, all set scores, statistics/CSV and original current-match score/person/session. Cancel and double taps must not add results. A future date or invalid match progression must not be accepted. Historical entry must not start a service, polling or screen retention.

Release suite: 265 core + 100 Android UI + 35 browser/relay = 400 checks. The 23 new core and 14 native view cases cover migration, authentic B3 fixtures, score-only persistence, mixed live/manual Undo/timing and complete no-keyboard workflows. Native screen appearance and wheel gestures on the physical OnePlus remain watch acceptance checks.

## v0.12.1 manual-set start-time check

Update in place to version code 13 using Settings → Check for updates. B4/M3/O1 and CSV/relay schemas are unchanged; retain every prior APK byte. With a fresh current match, review a manual set and cancel: no start should be recorded. Save it once: Current match should show the save time as its start, while the idle watch stays idle. Continue live, enter another set, Undo and reopen; the first start and timezone must stay unchanged. A current v0.12 match with no start also gains its start only on the next accepted manual set. Historical whole-match entry retains its chosen date and unknown times.

Release suite: 265 core + 100 native UI + 35 browser/relay = 400 checks. Four additional core and two native UI regressions cover save-only timestamps, real v0.12 untimed persistence, existing starts/zones, Undo/restart/new-match isolation and cancellation.

## v0.13 menu and Back acceptance

Update in place to v0.13.0 (code 14). B4/M3/O1, original package/signer, timestamps, CSV and relay security are unchanged. Retain v0.3–v0.12.1 APK bytes. Root menu has Current match, Results, Statistics, Opponents and Settings. Live Start/Pause/Resume is the first main action; Match options holds opponent, format, new match and ending early. Completed matches hide inapplicable scoring/setup actions.

Check NEW: format and optional name stay staged until Create match; replacement requires Replace & create match. Back to setup preserves the draft; Cancel returns to its caller. Current match → Enter set score and Results → Add past match are separate. Score next set live activates play; Enter next set score stays in manual entry.

Results → Options holds CSV and bulk deletion. Filters/page/scroll survive opening a result, picking an opponent and cancelling deletion. Each panel has one named Back/Cancel; hardware Back and native Wear swipe dismiss follow it. A Scoreboard header shortcut during play/resume does not change the session. Test actual right-swipe recognition and round-watch layout physically.

View results on phone, Edit names on phone and Import CSV open the matching phone tab. Their Back routes return to their caller. Returning to scoring while a link is being prepared must keep scoring visible when that request completes. The transfer stays reusable until expiry. No polling or extra background work is added.

Automated release suite: 265 core + 100 Android UI + 35 browser/relay = 400 checks, including 18 new navigation regressions and two phone launch-tab checks. Headless rendering and native callback wiring do not substitute for OnePlus gesture/comfort acceptance.

## Website-only privacy/download refresh — 7 November 2026

The current APK stays v0.13.0/code 14. Publish updated public/index.html, transfer empty-state wording and matching source/docs without rebuilding Android or altering release.properties. Only the current APK is linked on the landing page. Keep every retained APK byte, builds.json and SHA256SUMS.txt; removal of old page links is not deletion or archive pruning. Rollback delivery may require a new higher-version-code build with compatible data readers; preserved old APKs do not imply a safe in-place Android downgrade.

Verify anonymous transfer.html has no visible workspace/data, QR privacy copy explains link possession and ten-minute access, old page links are absent, current install/menu paths are accurate, all retained APK hashes match the pre-change inventory and source ZIP reflects the final copy. Run the unchanged browser/relay checks plus synthetic two-session isolation checks.
