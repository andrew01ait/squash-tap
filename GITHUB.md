# Internal and GitHub source workflow

Public repository: <https://github.com/andrew01ait/squash-tap>. Official download/update site: <https://squash-tap-andrew.endruuu.chatgpt.site/>. Current watch release: **v0.14.0 / code 15**.

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

## Building or contributing

See [DEPLOYMENT.md](DEPLOYMENT.md) for Linux build prerequisites and [TESTING.md](TESTING.md) for checks that can run without the official signing key. Keep dependency locks and migrations reviewed. Compare the final diff, tests, persistence/Undo behavior, privacy boundary and power policy before publishing changes.

The official release pipeline additionally requires the private original signing key and retained APKs restored/verified from the download site. They are deliberately absent from GitHub. `SIGNING_IDENTITY.json` retains only public certificate information in exports. An independent fork must configure its own signing/package/update/hosting identity; do not weaken official update checks or point a fork at another project's deployment identity.

The relay needs a Worker/server with D1/R2-compatible storage. A static-only GitHub Pages publication cannot implement it. Configure your own hosting/storage and runtime settings for a fork; do not add secrets to source or the hosting manifest.

## License and export naming

No project-wide open-source license has been selected. Preserve third-party notices and individual dependency licenses; do not add/change licensing without the owner's choice.

Future user-facing JSON filenames use the local export date and application name: `YYYYMMDD_ApplicationName_Description.json`, for example `20261109_SquashTap_results.json`. Current results interchange is CSV. This convention does not rename internal configuration files or add a JSON export feature.
