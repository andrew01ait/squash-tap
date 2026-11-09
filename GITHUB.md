# GitHub source repository

This repository contains the Squash Tap Wear OS app, public website, encrypted temporary-transfer relay, tests and build documentation. The initial import is the v0.13.0 source (Android version code 14), based on source revision `285ce94dcf46b57f090d7e60839fe6cede58d2dc`.

The official app downloads remain at <https://squash-tap-andrew.endruuu.chatgpt.site/>. Publishing this source does not change the installed app or its update service.

## Contents

- `android/src`, `android/res` and `android/AndroidManifest.xml`: native watch app.
- `android/test`, `android/ui-test` and `android/test-fixtures`: shared-core, native UI and synthetic interchange tests.
- `public/`: landing page, phone transfer UI and interchange modules.
- `worker/`, `db/` and `drizzle/`: transfer relay and schema-only database migrations.
- `scripts/`, `package.json` and `pnpm-lock.yaml`: pinned build tooling and dependencies.
- `README.md`, `DEPLOYMENT.md`, `ARCHITECTURE.md`, `FLOW.md`, `TESTING.md` and `CHANGELOG.md`: user/developer documentation and release history.

## Local checks

Use Node.js 22.13 or newer. These phone-transfer state tests do not need installed dependencies or an Android SDK:

```sh
node --test web-test/transfer-state.test.mjs
```

The full release pipeline uses Java 17, Python 3, Android Build Tools/platform 34, JUnit, Robolectric and the locked Node dependencies. See `DEPLOYMENT.md` and `TESTING.md` for the complete build/check procedure. The original release report records 265 core, 100 Android UI and 35 web/relay checks; those historical results are distinct from any checks run after cloning.

## Release and deployment prerequisites

This is a source repository. Generated APKs, source ZIP downloads, SDKs, dependency caches, credentials and release signing keys are not stored here. Retained official APK metadata and checksums remain in `public/builds.json` and `public/SHA256SUMS.txt`; an official release build requires restoring those retained APKs from the download site and verifying them against that metadata.

The original signing key is held separately by the maintainer. `SIGNING_IDENTITY.json` contains only public certificate/identity information. The release builder intentionally refuses to replace a missing signing key, because doing so would break updates for existing installations. Building an independent fork requires a separate signing identity and appropriate package/update/deployment configuration; it must not impersonate the official update service.

The source copy retains the `DB`/`BUCKET` binding declarations but omits the managed hosting project ID and private artifact references. Configure your own hosting/storage for a fork. Preserve the existing deployment identity when maintaining the official site; publishing this repository does not migrate hosting to GitHub Pages. The relay needs a server runtime with D1/R2-compatible storage and is not a static-only website.

No project-wide open-source license has been selected for this initial publication. Third-party notices and individual dependency licenses remain included. A license decision is separate from publishing the source.

## Export filenames

Any future user-facing JSON export must use the local export date and application name: `YYYYMMDD_ApplicationName_Description.json`, for example `20261109_SquashTap_results.json`. The current results interchange remains CSV; this convention does not add a new JSON feature or rename internal configuration files.
