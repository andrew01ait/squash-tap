# Versions and compatibility

Current official watch release: **v0.23.4**, Android version code **43**; package **`at.andrew.squashtap`**. `public/release.properties` is the machine-readable update feed; `public/builds.json` and `SHA256SUMS.txt` record retained artifacts.

The optional `spoken_scores` boolean is included in full backups. An older backup without the setting restores the default, Off. Match history, rally encodings and CSV formats keep their existing versions.

## Independent version numbers

| Identifier | Meaning |
| --- | --- |
| Watch version name/code | Installed Android app, shown in Settings. New official APKs require a monotonically higher version code and the original signer. |
| Hosted Site saved version/deployment | Website/relay/source-download publication. A docs-only website update does not change the watch version. |
| Git commit | Exact internal or GitHub source revision. The sanitized public copy has its own history and deliberately different private/deployment metadata. |
| Data/interchange schema | Persistence and CSV/transfer compatibility; must be reviewed independently of UI release numbers. |

The optional backup setting `player_sort` accepts `az`, `za` and `recent`. An older backup uses the alphabetical default.

## Current formats

Read B1–B5 books. Preserve B4 for unchanged squash-only state; use B5 for multisport/explicit serving or extended results. Preserve old M1/M2/M3 squash encodings; M4 stores format/initial service data. M5 preserves the actual rallies behind a corrected partial retirement score. O1 remains readable, O2 separates team roles, and both use the same stable player catalog. Retain once-only B1/B2/B3/B4/O1 backups. CSV schemas 1/2 remain squash-compatible; schema 3 adds explicit sport/mode/rules, seven-game results, team roles and known point/tie-break details. Schema 3 requires the updated watch. Ordinary envelope-1 encryption/session direction remains unchanged; envelope 2 adds bounded complete-backup payloads with separate version-bound AAD. See [RULES.md](RULES.md) for frozen rule presets.

Older versions may not understand new preferences or retirement/manual CSV. Do not promise safe downgrades. For corrections, normally deliver a higher-version-code APK with compatible readers and the same package/key. Never fabricate legacy timing, rally order, winning sets or reused references during migration.

Navigation v0.15 adds only the optional `match_ready_id` preference for created, unplayed matches; cancellation and menu changes preserve all existing book/name/CSV readers and formats. An unfinished match locks sport selection until completion or confirmed discard.

## Release retention

The oldest retained APK is v0.3.0/code 3. Preserve v0.3 and later artifacts byte-for-byte for recovery; link only the current APK on the landing page. Hiding links is not deletion. APK retention on the distribution site is separate from the source-only GitHub repository, which excludes binaries.

## Documentation-only changes

Documentation, source export and maintenance-tool updates do not by themselves require a new watch APK. Keep release metadata/checksums and old device evidence intact. Record the change and actual validation in [CHANGELOG.md](CHANGELOG.md) and [TESTING.md](TESTING.md); update the public source package and both code copies. Run [the documentation checker](scripts/check_docs.py) before publication.

## Required release records

Keep [README.md](README.md), [INSTALLATION.md](INSTALLATION.md), [FLOW.md](FLOW.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DEPLOYMENT.md](DEPLOYMENT.md), [TESTING.md](TESTING.md), [GITHUB.md](GITHUB.md), [AGENTS.md](AGENTS.md) and [CHANGELOG.md](CHANGELOG.md) aligned with the final implemented behavior. Historical release entries remain historical; do not overwrite old suite totals with new ones.

## v0.16 compatibility

Keep Score is the new display name. The package `at.andrew.squashtap`, SharedPreferences `squash`, original signing certificate, old crypto context and old published APK names remain stable for in-place updates. Current generated downloads use `KeepScore-0.16.4.apk` and `KeepScore-source.zip`; old `SquashTap-*.apk` files remain byte-for-byte. No repository/website URL rename or license change accompanies this release.

ScoreState 4 adds pickleball’s server-dependent rally log while M4/B5 and CSV schema 3 retain their version identities with validated pickleball formats. Older watches must update before importing pickleball. New optional preferences include own name, favourites, palette, haptics and reminders; full-backup schema 1 carries only validated settings. Envelope 2 does not replace envelope 1. Live audience schema 1 is a separate read-only protocol/table with an eight-hour expiry and independent roles. Never reuse phone-transfer capabilities for an audience link.

The v0.16.1/code 19 patch changes navigation and sharing presentation, not match-book/CSV/backup schemas. The existing encrypted live schema 1 gains a minimal terminal discard notice; new viewer code also clears stale scores for older watches’ revoked links. Existing audience pages should be refreshed once to load the corrected viewer. Old notices remain readable only until the original link expiry.

The v0.16.2/code 20 patch changes shared menu branding to Keep Score. Sport names, navigation, scoring, persistence and transfer schemas retain their existing behavior; retain v0.16.1/code 19 and earlier APKs unchanged.

The v0.16.3/code 21 release adds alphabetical sport presentation, a bounded optional previous-meeting summary in live schema 1, and owner `completedAt` metadata for final-result expiry. Migration 0002 adds `live_games.active_expires_at` without deleting existing sessions. Old clients that omit completion metadata retain their prior expiry. Updated clients show final results for ten minutes and preserve the original live deadline for Undo. No match-book, CSV or backup schema changes; retain all earlier APKs byte-for-byte.

The v0.16.4/code 22 release changes result-filter presentation/reset only: player/date/mode clear together while sport and stored data remain intact. Phone statistics refresh after reset. No scoring, book, backup, CSV, relay or database migration changes accompany this patch; retain all prior APKs unchanged.

## Play preparation candidate

The separate `play-release.json` reserves 0.17.0-play.1/code 23 for a preparation-only Play AAB targeting API 35/minimum API 30. The historical direct release at the time of that preparation was v0.16.4/code 22/API 34. This is a distribution profile, not a package rename or data/schema migration. Future official versions across both channels must use monotonically increasing codes; do not publish a different app under code 23 or overwrite an existing APK. Play App Signing must retain the original app certificate for existing installations. Physical APK-to-Play upgrade and Console review remain pending; see [PLAY_RELEASE.md](PLAY_RELEASE.md).

## Linked matches release

The v0.18.0 direct APK uses code 24 and the existing package and signer. Code 23 remains assigned to the earlier Play preparation artifact. The separate room schema uses migration 0003. Existing B1–B5 books, transfer QR protocols, CSV schemas, and audience links remain readable.

The v0.18.1 direct APK uses code 25 and the original signer. Its navigation and responsive interface updates use the existing room, audience and transfer protocols. All 21 prior direct APKs retain their published checksums.

## Browser result entry · direct v0.19.0 / code 26

Older watches continue to accept supported new results through their existing CSV import. Editing an existing result requires the explicit result-edit capability. CSV schemas and encryption envelopes keep their existing versions. When the currently displayed retired match receives a different partial score, its B5 book can contain an M5 state. M5 wraps the original retired state and the two corrected partial totals. Undoing that retirement restores the recorded rallies after confirmation. All other current states continue using their existing encodings. A v0.19.0 or newer watch is required to restore a backup that contains M5.

## Device management · direct v0.20.0 / code 27

New rooms use independent grants for each watch installation and referee browser. Update all player watches before starting a managed room. Existing rooms, old referee links, score formats, book storage and the phone journal remain compatible.

## Grouped watch menus and score layout · direct v0.20.1 / code 28

This patch revises watch navigation and adapts score and touch geometry to round and rectangular displays. It uses the existing score, backup, room and relay formats.

## NFC handover and scorecards · direct v0.21.0 / code 30

NFC uses the existing room invitations and HTTPS page links. Older watch versions can use the same manual code and browser pages. The website highlights completed periods in records from previous versions and exports the redesigned PNG directly in the browser.

## Watch menu consistency · direct v0.20.2 / code 29

The updated menus and player pickers use existing match identifiers, settings and score records. Direct updates use the original signing certificate.

## NFC page sharing · direct v0.21.1 / code 31

The watch update changes NFC routing and reader compatibility. The audience, referee and journal links use the same record and page permissions as their QR codes.

## NFC reader compatibility · direct v0.21.2 / code 32

The watch accepts both Android Type 4 NDEF application identifiers. Each reader receives the capability version it selected. The diagnostics for an explicit handover record whether the reader reached the app.

## Diagnostic QR · direct v0.21.3 / code 33

The text uses the existing gzip fragment format. A second fragment field carries the current watch and match state. The phone also accepts links with only the original text field. The watch automatically trims the stored diagnostic event log during an update’s first launch and on every subsequent event.

## NFC diagnostics and update progress · direct v0.21.4 / code 34

Each completed NFC sharing attempt records its end reason and elapsed seconds in the existing local diagnostic event. The direct updater brings its current status into view. This release uses the existing diagnostic QR format and match storage.

## Host watch cancellation · direct v0.21.6 / code 36

The host watch uses the existing referee-room revocation and offline retry. Other devices can continue using their existing versions.

## Watch update progress and diagnostic QR · direct v0.21.5 / code 35

The update feed and diagnostic link formats are unchanged. The installed watch app draws the progress indicator, so the first download using the new bar is an update started from v0.21.5 or later. The QR keeps the existing 318-byte limit and gives an NFC sharing event priority over subsequent activity lifecycle entries.
