# Versions and compatibility

Current official watch release: **v0.15.0**; Android version code **16**; package **`at.andrew.squashtap`**. `public/release.properties` is the machine-readable update feed; `public/builds.json` and `SHA256SUMS.txt` record retained artifacts.

## Independent version numbers

| Identifier | Meaning |
| --- | --- |
| Watch version name/code | Installed Android app, shown in Settings. New official APKs require a monotonically higher version code and the original signer. |
| Hosted Site saved version/deployment | Website/relay/source-download publication. A docs-only website update does not change the watch version. |
| Git commit | Exact internal or GitHub source revision. The sanitized public copy has its own history and deliberately different private/deployment metadata. |
| Data/interchange schema | Persistence and CSV/transfer compatibility; must be reviewed independently of UI release numbers. |

## Current formats

Read B1–B5 books. Preserve B4 for unchanged squash-only state; use B5 for multisport/explicit serving or extended results. Preserve old M1/M2/M3 squash encodings; M4 stores format/initial service data. O1 remains readable, O2 separates team roles, and both use the same stable player catalog. Retain once-only B1/B2/B3/B4/O1 backups. CSV schemas 1/2 remain squash-compatible; schema 3 adds explicit sport/mode/rules, seven-game results, team roles and known point/tie-break details. Schema 3 requires the updated watch. Phone encryption/session direction remains unchanged. See [RULES.md](RULES.md) for frozen rule presets.

Older versions may not understand new preferences or retirement/manual CSV. Do not promise safe downgrades. For corrections, normally deliver a higher-version-code APK with compatible readers and the same package/key. Never fabricate legacy timing, rally order, winning sets or reused references during migration.

Navigation v0.15 adds only the optional `match_ready_id` preference for created, unplayed matches; cancellation and menu changes preserve all existing book/name/CSV readers and formats. An unfinished match locks sport selection until completion or confirmed discard.

## Release retention

The oldest retained APK is v0.3.0/code 3. Preserve v0.3 and later artifacts byte-for-byte for recovery; link only the current APK on the landing page. Hiding links is not deletion. APK retention on the distribution site is separate from the source-only GitHub repository, which excludes binaries.

## Documentation-only changes

Documentation, source export and maintenance-tool updates do not by themselves require a new watch APK. Keep release metadata/checksums and old device evidence intact. Record the change and actual validation in [CHANGELOG.md](CHANGELOG.md) and [TESTING.md](TESTING.md); update the public source package and both code copies. Run [the documentation checker](scripts/check_docs.py) before publication.

## Required release records

Keep [README.md](README.md), [INSTALLATION.md](INSTALLATION.md), [FLOW.md](FLOW.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DEPLOYMENT.md](DEPLOYMENT.md), [TESTING.md](TESTING.md), [GITHUB.md](GITHUB.md), [AGENTS.md](AGENTS.md) and [CHANGELOG.md](CHANGELOG.md) aligned with the final implemented behavior. Historical release entries remain historical; do not overwrite old suite totals with new ones.
