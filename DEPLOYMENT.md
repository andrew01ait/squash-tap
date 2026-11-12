# Build and deployment

Official watch release: **v0.16.4 / code 22**. User installation is in [INSTALLATION.md](INSTALLATION.md); release-number and compatibility rules are in [VERSIONING.md](VERSIONING.md).

## Repositories and prerequisites

The internal Site repository retains the official hosting identity, immutable APK archive and private recovery references. [GitHub](https://github.com/andrew01ait/squash-tap) is the sanitized public source copy. Its export omits the official hosting project ID, private artifact references and generated APK/ZIP files. There is no automatic two-way synchronization or GitHub deployment. See [GITHUB.md](GITHUB.md) before moving changes between them.

Build-machine prerequisites (not required on the user's phone/PC): Linux, Java 17 with `jdk.compiler`/`keytool`, Python 3, Node.js >=22.13, the project's pinned pnpm 11.25.0, Android Build Tools 34/platform 34 and access to the official dependency repositories. Native UI testing prepares Maven/Robolectric dependencies in ignored `.local/`.

Restore dependency inputs using the committed `pnpm-lock.yaml`, `android-dependencies.json` and `android/ui-test/pom.xml`. Use the Sites dependency helper in its managed environment, or the portable project installer:

```sh
npm run install:ci
python3 scripts/bootstrap_android.py
```

The bootstrap verifies downloaded SDK/test artifacts. `scripts/androidx_deps.py` prepares and verifies locked runtime artifacts; do not regenerate its lock for routine builds. No installed dependency cache, SDK, credential or proxy settings belong in source exports.

## Signing and retained releases

Official rebuilds require the existing private `.local/signing/squash-tap.p12` and `.local/signing/key.password`. Restore them through the maintainer's private recovery process before building; never generate a replacement key for existing installations. The public certificate fingerprint is in [SIGNING_IDENTITY.json](SIGNING_IDENTITY.json); the GitHub/source-ZIP copy excludes private backup identifiers.

Retain every APK listed in `public/builds.json`, starting with v0.3.0/code 3, byte-for-byte. GitHub intentionally does not contain those generated binaries. Restore missing release files from the official site's `/apks/<filename>` paths and verify each against its recorded size/SHA-256 before an official build. The builder refuses changed/missing retained artifacts and checks the new APK's signer, package, version and alignment. Historical APKs are not rebuilt from current source.

## Watch build and full release checks

After restoring dependencies, signing material and retained APKs:

```sh
python3 scripts/build.py --sdk .local/android-sdk --junit .local/android-sdk/junit.jar --hamcrest .local/android-sdk/hamcrest-core.jar
```

The builder runs core tests, browser/native interchange and relay tests, then compiles Android resources/classes, runs native UI checks and performs D8/alignment/signing. It updates `public/release.properties`, `builds.json`, `SHA256SUMS.txt` and the public release report. Do not overwrite an already published version: advance `RELEASES`, version code and release notes for the next app release first.

Independent forks need their own package/signing/update/hosting configuration. The official builder deliberately protects the installed signing identity; publishing the source does not provide that private key. Do not silently disable identity validation to make a fork appear to be an official update.

## Documentation and public source artifacts

The documentation set must describe the same release in both repositories. Run:

```sh
python3 scripts/check_docs.py
python3 scripts/package_source.py
```

The source ZIP includes documentation, complete application/site sources, fixtures, lockfiles and notices. It sanitizes hosting/signing metadata and excludes private artifacts, SDKs, caches, build outputs, Git metadata and APKs. To prepare a matching GitHub source copy in an empty directory:

```sh
python3 scripts/package_source.py --github-dir /absolute/path/to/empty-public-source
python3 scripts/check_docs.py --compare /absolute/path/to/empty-public-source
```

Review that export and publish its diff against the current GitHub branch using normal commits and a checked expected head. Never replace the internal repository with the sanitized public copy: preserve the official hosting identity, private recovery records and retained binaries.

## Website publication

The official public site is <https://squash-tap-andrew.endruuu.chatgpt.site/>. Preserve its current project ID/audience and `DB`/`BUCKET` bindings from the internal `.openai/hosting.json`; do not register a replacement site. Keep the landing page simple, with only the current APK link, features and installation/privacy wording. Historical APKs remain retained even though their links are hidden.

For a frontend/relay/docs-source update, regenerate the source ZIP, then build:

```sh
npm run build
```

The supported output is a Worker ES module at `dist/server/index.js` with static assets; `dist/` is generated. Schema changes use committed schema-only Drizzle migrations. Never create tables from request handlers or place runtime secrets in the hosting manifest.

Use the Sites source workflow to commit/push the exact tested source and package its matching output. Save that source revision, then deploy the saved version to the existing public audience and wait for a successful deployment status. If local build/package preparation is unavailable, the source-only remote-build fallback must use the verified pushed revision; report any unverified local checks accurately. Never deploy an archive from a different commit or delete real transfer data to test deployment.

A documentation-only publication may update the downloadable source ZIP and the hosted site version. It does not require a new APK/version code, change the update feed or establish a new native-device test result.

## Release procedure

1. Review the current app/code/schema and intended change against [AGENTS.md](AGENTS.md). Preserve score, Undo, names, timing, counters and tombstones; add and test migrations when needed.
2. Advance version code/name and notes for a new APK only. Restore the original signing key and verify retained releases.
3. Run the meaningful native/web release checks in [TESTING.md](TESTING.md). Inspect signature, package/SDK/watch metadata, alignment and all release hashes.
4. Update README, architecture, flows, testing evidence, versioning, deployment, maintenance rules and changelog together. Record actual checks and physical limitations separately.
5. Regenerate/check the sanitized source ZIP and GitHub export. Review public metadata, privacy and third-party notices.
6. Publish the internal source/site revision and the reviewed GitHub diff. Verify deployed status and public file hashes/links.
7. Use the existing phone-only install/updater flow for physical acceptance. Keep existing app data and note the tested device/version; do not assume old user reports validate a new release.

## Rollback and operational limits

Preserved APKs enable recovery analysis but do not imply a safe Android downgrade. Newer preferences may be unreadable by old versions; a corrective release normally needs a higher version code and compatible readers. Website rollback must retain a compatible relay/CSV contract and source provenance. Never uninstall the watch app as a routine update step.

Transfers expire after ten minutes; physical cleanup of unused ciphertext occurs on later API traffic. There is no independent timed erasure job or permanent history service. Health checks and complete transfer tests use synthetic sessions only. Application updates and transfers remain explicit actions, without background polling.

## Multisport release

The original package/signing identity remains unchanged. RULES.md documents formats and limitations. A native release must test unfinished/paused/prepared sport locks, stale selection/create callbacks, confirmed discard versus retirement/deletion, contextual shared-screen Back/swipe, explicit choice lists, all six sports, both badminton formats, padel deuce options, Undo across tennis game/set boundaries, service rotation, B4/B5 and O1/O2 migration, team identities, scope/filter/delete behavior, and native/browser CSV schema-3 parity. Preserve old CSV readers and all historical APK bytes.

## Keep Score release additions

The Android runtime lock now includes Tiles 1.2.0 and watchface complication data source 1.2.1 plus their pinned dependencies. The resolver compares stable/prerelease versions correctly and includes Kotlin 1.8.22 explicitly. The APK/source notice includes the relocated Protocol Buffers BSD notice as well as the existing Apache notices. Preserve all SHA-256 locks and the same signing identity.

`drizzle/0001_breezy_rawhide_kid.sql` adds only `live_games` and its expiry index; migration 0000 remains immutable. Apply pending migrations to the local preview in order using Wrangler’s local `d1 execute DB` with `dist/server/wrangler.json` and `.wrangler/state`. Sites applies hosted migrations during publication. Do not initialize tables in request handlers or seed real player data.

The v0.16 source package is `public/KeepScore-source.zip`; the APK is `public/apks/KeepScore-0.16.4.apk`. Preserve earlier APK names/hashes. Verify the landing page’s six PNG sport previews plus the three colour schemes and all currently referenced downloads before publication. Full backup envelope 2 and live schema 1 are separate from the original envelope-1 transfer; keep both reader contracts and access roles.

For v0.16.1 the live relay/schema are unchanged. Terminal notices use the existing encrypted owner PUT protocol; only the native client/viewer and navigation change. Preserve the v0.16.0/code 18 APK byte-for-byte while adding code 19.

The v0.16.2/code 20 patch changes shared menu branding to Keep Score. Sport names, navigation, scoring, persistence and transfer schemas retain their existing behavior; retain v0.16.1/code 19 and earlier APKs unchanged.

The v0.16.3/code 21 release adds alphabetical sport presentation, a bounded optional previous-meeting summary in live schema 1, and owner `completedAt` metadata for final-result expiry. Migration 0002 adds `live_games.active_expires_at` without deleting existing sessions. Old clients that omit completion metadata retain their prior expiry. Updated clients show final results for ten minutes and preserve the original live deadline for Undo. No match-book, CSV or backup schema changes; retain all earlier APKs byte-for-byte.

The v0.16.4/code 22 release changes result-filter presentation/reset only: player/date/mode clear together while sport and stored data remain intact. Phone statistics refresh after reset. No scoring, book, backup, CSV, relay or database migration changes accompany this patch; retain all prior APKs unchanged.

## Separate Google Play preparation

The public watch feed remains v0.16.4/code 22. Preparation candidate 0.17.0-play.1/code 23 targets API 35 and outputs an AAB under ignored `build/play/`, without publishing an APK or updating release metadata. With a complete JDK 17, original signing material, retained APKs and the existing Node dependencies restored:

```sh
python3 scripts/bootstrap_android.py
python3 scripts/bootstrap_play.py
python3 scripts/verify_play.py
```

Play tooling/downloads and installed critical files are SHA-256 pinned in `play-build-tools.json`; runtime AndroidX pins remain unchanged. The existing supported AAPT2/bundletool command-line pipeline avoids an unrelated Gradle migration. The public source excludes generated AAB/APKS files and private signing material. Configure Play App Signing with the existing app key and register a separate upload key before actual submission. Do not upload the direct APK updater to Google Play. The candidate has not been submitted and is not promoted by website deployment. See [PLAY_RELEASE.md](PLAY_RELEASE.md).
