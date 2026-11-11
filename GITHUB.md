# Internal and GitHub source workflow

Public repository: <https://github.com/andrew01ait/squash-tap>. Official download/update site: <https://squash-tap-andrew.endruuu.chatgpt.site/>. Current watch release: **v0.16.3 / code 21**.

## Two copies and their responsibilities

| Copy | Contains | Publication rule |
| --- | --- | --- |
| Internal Site source | Application/site code, docs, official hosting identity, retained APKs and private recovery references. Actual signing material remains outside Git. | Authoritative source for the current official deployment. Preserve its project, audience, release history and signing continuity. |
| GitHub/source ZIP | Same reviewed application/site sources, tests, docs, fixtures, lockfiles and third-party notices. Sanitized hosting/signing metadata; no generated APKs/ZIPs or private artifact references. | Public source mirror with its own commit history. Review and synchronize explicitly; there is no automatic deployment or two-way merge. |

Both copies use the same Markdown documentation. Private artifact identifiers and the official hosting project ID are metadata differences, not separate feature implementations. `scripts/package_source.py` produces the reviewed public copy without changing the internal originals.

## Source map

- `android/src`, `android/res`, `android/AndroidManifest.xml`: native Wear OS app.
- `android/test`, `android/ui-test`, `android/test-fixtures`: core, native UI and synthetic compatibility tests.
- `public/`: landing page, phone UI, diagnostics decoder and interchange modules.
- `worker/`, `db/`, `drizzle/`: encrypted temporary relay and schema-only migrations.
- `app/`, `components/`, `hooks/`, `lib/`, `vendor/`: website integration, shared assets and dependency notices.
- `scripts/`, `package.json`, `pnpm-lock.yaml`, `android-dependencies.json`: reproducible build/test/source-export inputs.
- The documentation index is in [README.md](README.md); automation constraints are in [AGENTS.md](AGENTS.md).

## Preparing and checking a public update

1. Read the latest internal and GitHub heads before editing. Bring any incoming GitHub code changes into the internal source through a reviewed merge/cherry-pick/diff; never overwrite concurrent work.
2. Make the final source/doc changes in the internal checkout. Retain its hosting/signing recovery metadata and release archive. Run checks appropriate to the change; a new APK needs the full release checks.
3. Create an empty export directory and run:

   ```sh
   python3 scripts/check_docs.py
   python3 scripts/package_source.py --github-dir /absolute/path/to/empty-public-source
   python3 scripts/check_docs.py --compare /absolute/path/to/empty-public-source
   ```

4. Review the export/diff. The exporter strips private artifact references and the official hosting project ID, adds source-only ignore rules, includes required website components/docs and preserves file modes. Never copy the internal `.git` history, `.local` signing/cache directory, real user exports or generated APKs.
5. Publish normal GitHub commits against the observed branch head; use a checked expected head and no force push. Keep unrelated existing files/changes. Verify the new head, file list and uploaded blob hashes against the reviewed export.
6. Publish any matching official website/source-download changes through the existing Site source/version/deployment workflow. GitHub publication alone does not update the live app or website.

The GitHub connector/tool must actually have write authorization; a listed tool or repository-level account permission does not establish that a write succeeded. Report a rejected write honestly and recheck only after authorization changes. Use secure account authorization, not credentials pasted into chat or committed to files.

The multisport/navigation work is reviewed in [draft PR #1](https://github.com/andrew01ait/squash-tap/pull/1) on `feature/multisport-v0.14.0`; main remains separate until an explicitly authorized merge. Keep the PR title/description and source export aligned with the latest release.

## Building or contributing

See [DEPLOYMENT.md](DEPLOYMENT.md) for Linux build prerequisites and [TESTING.md](TESTING.md) for checks that can run without the official signing key. Keep dependency locks and migrations reviewed. Compare the final diff, tests, persistence/Undo behavior, privacy boundary and power policy before publishing changes.

The official release pipeline additionally requires the private original signing key and retained APKs restored/verified from the download site. They are deliberately absent from GitHub. `SIGNING_IDENTITY.json` retains only public certificate information in exports. An independent fork must configure its own signing/package/update/hosting identity; do not weaken official update checks or point a fork at another project's deployment identity.

The relay needs a Worker/server with D1/R2-compatible storage. A static-only GitHub Pages publication cannot implement it. Configure your own hosting/storage and runtime settings for a fork; do not add secrets to source or the hosting manifest.

## License and export naming

No project-wide open-source license has been selected. Preserve third-party notices and individual dependency licenses; do not add/change licensing without the owner's choice.

Future user-facing JSON filenames use the local export date and application name: `YYYYMMDD_ApplicationName_Description.json`, for example `20261111_KeepScore_Backup.json`. Current results interchange is CSV. Complete backup JSON uses this naming convention; internal configuration filenames remain unchanged.

The v0.16 mirror includes Keep Score branding, six sports including pickleball, own name, palettes, full backup/restore, live audience viewer/API, tiles/complication, result cards and their tests/docs. Continue using the existing draft PR #1 until merge is explicitly authorized; changing the app display name does not rename the repository.

The v0.16.1 patch adds in-place Settings selection, guarded automatic audience QR presentation, encrypted anonymous discard notices/reconnect retry and ended-view clearing, with native/web regressions. Sync this patch into the existing draft PR without merging main.

The v0.16.2 patch replaces All Sports identity badges and app section labels with Keep Score, including shared selectors. Sport-scoped screens keep their sport label/icon. Keep the sanitized source and existing draft PR aligned without merging main.

The v0.16.3 mirror adds alphabetical watch/phone sports, bounded matching-player head-to-head/last meeting, accessible audience share/copy icons and ten-minute final results with stable expiry, Undo and reconnect behavior. Include the deadline migration and native/browser tests; keep the existing draft PR without merging main.
