# Google Play preparation

The public watch release remains **v0.16.4 / code 22**. The separate **0.17.0-play.1 / code 23** candidate is preparation only, targets API 35 and has not been submitted to Play Console. Its version code is reserved across distribution channels; any later official release must advance monotonically.

## Prepared implementation

- Keep package `at.andrew.squashtap`, the original app signer, the `squash` preference namespace and all book/CSV/backup contracts.
- Generate a Play manifest targeting API 35 with minimum API 30. Preserve watch/standalone declarations, Tile preview, complication and the explicit ongoing match service.
- Omit both APK installation permissions and InstallReceiver. Compile a Play-only UpdateClient with no network, package download or PackageInstaller implementation. Guard all old update entry points; Settings → Check for updates opens a Google Play update panel.
- Use a Play-only black scoreboard/startup background and a launch icon matching the app icon, with a 48dp legacy starting icon. The direct theme is unchanged.
- Leave the direct distribution's updater and original manifest separate. Existing public APKs/feed/checksums stay byte-for-byte.
- Use checksum-pinned Google SDK/Maven AAPT2 and bundletool to build a valid signed AAB and generate a same-signer universal APK for inspection. The existing native build remains in place; a Gradle migration is not required for the supported command-line bundle workflow.
- Verify same package/signer, higher version code, minimum/target SDK, missing installer permissions/implementation, absence of native libraries and ZIP alignment. A future JNI dependency fails closed until its native/64-bit packaging is reviewed.

## Reproduce preparation

Maintainer prerequisites: a complete JDK 17, Python 3 and the existing pinned Node dependencies. The public source does not include the original private app key or retained APKs; restore those for official verification as described in [DEPLOYMENT.md](DEPLOYMENT.md).

```sh
python3 scripts/bootstrap_android.py
python3 scripts/bootstrap_play.py
python3 scripts/verify_play.py
```

The verified download lock is [play-build-tools.json](play-build-tools.json); do not silently update its checksums. [play-release.json](play-release.json) declares the candidate identity. Generated files stay under ignored `build/play/`, including the AAB, locally generated test APK, validation reports and verification results. Nothing is automatically uploaded to Google Play or promoted to the website's APK update feed.

The full verification runs shared core tests, web/relay tests, direct-distribution native tests, and Play-distribution native tests including API 34/35 update/state scenarios. Automated evidence does not establish a physical in-place APK-to-Play upgrade, actual Play Store delivery or policy approval.

## Signing enrollment before the first upload

Configure Play App Signing with **our existing app signing key**, not an automatically generated replacement. Compare the certificate against [SIGNING_IDENTITY.json](SIGNING_IDENTITY.json), then register a separate upload key through the secure maintainer process. The preparation AAB is signed with the existing app key; it has not registered an upload key or enrolled a Play account.

Test an actual Play-delivered update over the installed APK on a backed-up test watch. Check saved history, players, settings, active scores/Undo, tiles, complication, pause/resume and notifications. Signature equality and increasing version codes establish compatibility prerequisites, not proof of an actual Store update. Do not uninstall a real user's app to make the test pass.

## Release gates

| Item | State / next action |
| --- | --- |
| Developer owner and account | Owner must choose the actual publisher, complete identity/device verification, accept Google's agreement and pay the one-time registration fee. Organization accounts generally need a D-U-N-S number. |
| Trader status | Declare accurately for the actual activity; new accounts must do this at creation. Confirm public contact details before publication. |
| Play App Signing/upload key | Complete enrollment using the original app signing certificate, with a separate upload key. |
| Privacy policy | Review [the draft](play/privacy-policy-draft.md), supply publisher/contact details, audit provider logs/retention, publish a complete public HTML policy and add its in-app access. This is still a submission blocker. |
| Data safety | Audit encrypted payloads separately from metadata, IP/rate-limit records, provider logs, speech services and dependencies. Qualifying end-to-end encryption has an exception; do not infer a blanket no-data claim. |
| App content | Complete content rating, audience, ads, app access and health declarations accurately. Scorekeeping is not a claim to record workouts or measure health. |
| Foreground service | Review specialUse eligibility and submit its declaration plus a demonstration video. Current manifest use is not an approval. |
| Store listing | Review [listing copy](play/store-listing-draft.md), finish icon/feature graphic, and capture current unframed square watch screenshots including the Tile/complication. |
| Device acceptance | Test small/large round displays, increased fonts, current 64-bit Wear OS, OnePlus/Pixel/Samsung, launch appearance, swipe/back, ambient, offline play and live reconnect/final expiry. Physical acceptance remains pending. |
| Closed testing | If a personal account was created after 2023-11-13: recruit at least 12 genuine testers, keep opt-in continuous for 14 days, collect feedback, then apply for production access. Use Wear OS users who can exercise the app. |
| Wear OS review | Add the Wear OS form factor, use its test track, supply reviewer steps/video and opt into the Wear OS quality review. |
| Hosted sharing | Before wider rollout, review relay capacity/rate limits, reliability, support and actual hosting/logging arrangements. Never promise permanent cloud backups or scheduled exact-time erasure. |

## Foreground-service review draft

Function: the user explicitly starts or resumes a racket-sport scorekeeping session. The ongoing notification provides a persistent return shortcut while the match is active; it ends when play pauses/completes or the session is dismissed. Idle menus/history do not start it.

Impact if interrupted: the user loses the ongoing match shortcut/active-session availability while playing. Saved scores must still survive interruption. Do not claim continuous health/location tracking or a CPU wake lock.

Video plan: start an unfinished match, score points, leave the foreground, show the ongoing entry, return through it with the score intact, then pause and finish/discard and show the ongoing entry removed. Record the actual candidate on a watch or emulator; no demonstration has yet been recorded.

## Launch test plan

1. Back up a test watch on v0.16.4. Prepare saved singles/doubles across sports, long/archived player names, favourites, a palette and an unfinished paused match with Undo.
2. Install through Play's test track using the matching account/signer and a higher version code. Confirm every saved value and active score, without an uninstall.
3. Play and complete each sport, including deuce/tie-breaks, pickleball side-outs, corrections, retirement and discard. Test small fonts/large fonts and every Back/swipe route.
4. Test screen-off/ambient, background/return, pause/resume, notification denial, Tile and complication. Record real battery/brightness feedback separately from automated tests.
5. Exercise phone CSV/backup/restore, temporary link expiry, live QR/share, previous meetings, offline reconnect, final ten-minute availability and discard/stop.
6. Exercise Play update navigation with the Store available/unavailable. Confirm the Play app has no APK install prompt or website update fallback.
7. Collect genuine tester feedback and fix material issues before production access and Wear OS review.

## Official references checked 2026-11-12

- [Current target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Wear OS publishing](https://developer.android.com/training/wearables/packaging) and [form factor tracks](https://support.google.com/googleplay/android-developer/answer/13295490)
- [Command-line app bundles](https://developer.android.com/build/building-cmdline) and [Play App Signing](https://developer.android.com/studio/publish/app-signing)
- [APK installation permission policy](https://support.google.com/googleplay/android-developer/answer/12085295)
- [Closed testing](https://support.google.com/googleplay/android-developer/answer/14151465) and [developer registration](https://support.google.com/googleplay/android-developer/answer/6112435)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [User Data/privacy policy](https://support.google.com/googleplay/android-developer/answer/10144311), [FGS declarations](https://support.google.com/googleplay/android-developer/answer/13392821), [health declaration](https://support.google.com/googleplay/android-developer/answer/14738291) and [trader declaration](https://support.google.com/googleplay/android-developer/answer/17594752)
- [Wear OS quality](https://developer.android.com/docs/quality-guidelines/wear-app-quality) and [store assets](https://support.google.com/googleplay/android-developer/answer/9866151)

Recheck Console requirements before submission; this document records preparation, not approval or a promise of a launch date.
