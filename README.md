# Squash Tap

A standalone squash scorekeeper for Wear OS. Current release: **v0.13.0** (Android version code 14).

**[Download the watch app](https://squash-tap-andrew.endruuu.chatgpt.site/)** · **[GitHub source/setup notes](GITHUB.md)**

This repository includes the watch app, website, encrypted phone-transfer relay, tests and build documentation. Release signing keys and generated APKs are kept separately; see [GITHUB.md](GITHUB.md) before building or deploying a fork.

## Playing a match

- Tap the blue **ME** or red **OPPONENT** card to award a rally. Me is always the first score in results.
- To change the current unfinished match, tap **MENU → Current match → Match options → Match format → Best of 3 / Best of 5**. Points, set scores and Undo history are retained. A completed match keeps its format; switching BO5 to BO3 is blocked once someone has two set wins.
- To start a fresh match, choose **NEW**, optionally select an opponent, select **Best of 3** (first to 2 sets) or **Best of 5** (first to 3), then **Create match**. Replacing an unfinished match asks for confirmation.
- Each set uses point-a-rally scoring to 11, with a two-point lead required. After winning a set, tap **NEXT** to begin the next one. The previous set’s score is retained.
- **UNDO** corrects the last rally, including a winning point. Undo from an empty next set returns to the preceding set. The history survives reopening and normal in-place updates.
- The header shows sets won and opens **MENU**. The Menu has **Current match**, **Results**, **Statistics**, purple **Opponents** and grey **Settings**. Current match puts Start/Pause/Resume first; Match options holds format, opponent, replacement and ending early. Settings contains version, updates, display/notification options and diagnostics.
- Timing starts on the first scored point, or when you save a manually entered set if no start is recorded, and finishes on the final live winning point. The result title is the local start time. Details show one date with Start/End times; an overnight match also shows its end date. Duration includes set breaks and pauses. Timing survives reopening. Undo preserves the start and records a new finish when the corrected match ends.
- The final winning rally automatically saves the match and opens its result page. **MENU → Results** lists past matches by start date/time, with duration, the daily match reference, final score and every set score.
- Results use blue/red cards with start date/time, duration, a secondary match reference and final score: blue when Me won, red when Opponent won. Match actions are blue, person selectors purple, Settings actions are neutral grey, deletion actions and their confirmations are red, and Back/Cancel controls use a distinct outline. Styles are explicit rather than inherited from the surrounding panel.
- Match numbers start at 1 each local day. Deleting results never renumbers or reuses numbers. Correcting the current completed match through Undo retains its original date/number when it finishes again.
- Each result has a confirmed **Delete this result** action. **Results → Options → Delete all results** also asks for confirmation and keeps the active scoreboard. A deleted current result stays deleted even after reopening or correcting it.

Updating from v0.1/v0.2 migrates the existing score and Undo history into the first set of a best-of-three match. An already won old game counts as one set; it does not invent a completed match. The old serialized score remains as a recovery copy. v0.6 reads the previous B1 match-book format and writes B2 with timing; the original B1 value is saved once as `match_book_v1_backup`. Older completed matches retain their results with missing timing shown as **Not recorded**. A match already underway before the update can record its finish but cannot recover its start or duration; start a new match to test full timing.

## Opponents, filters and browser transfers — v0.8

Names are optional. MENU → Current match → Match options → Opponent selects No name or a saved person. Add opponent offers typing and Speak name; always review speech spelling before Save. MENU → Opponents centrally adds, renames and removes names. Renaming updates linked results. Removing a name hides it from future selection but keeps its identity on past results and in historical filters; the phone page can restore removed names. Existing unnamed results can be assigned a person from their result page. This never resets points, sets or Undo.

MENU → Results → Filters combines an opponent (All, Unnamed, or a saved/removed person) with Today, Last 7 days, This month, or an inclusive custom date range. The match's recorded start zone determines its day; legacy untimed results use the recorded completion date. Results → Options → Export CSV offers All results or Filtered results. End an existing transfer before exporting a different selection.

1. Open MENU → Results → Options → View results on phone → Show phone QR on the watch.
2. Scan with the phone camera and open the HTTPS link. Both devices need internet; a phone app, account and debugging connection are unnecessary.
3. On the phone, download all received results or filter before Download CSV. The Opponents tab edits names and the current match's optional opponent. The Import CSV tab selects and validates a Squash Tap CSV; an empty template is available.
4. Tap Send changes once on the phone, then Receive changes on the watch. Review names, the current opponent and counts of new, duplicate, conflicting and renumbered results. Apply changes commits scores and opponent metadata together; Cancel applies nothing.
5. A successful apply ends the transfer. Use End transfer to close it manually, or let its ten-minute link expire. Starting the app alone performs no transfer calls; there is no background polling.

CSV contains completed results, opponent ID/name, completion date/reference, match format, UTC start/finish, recorded zone, elapsed seconds, sets won and each set score. It supports UTF-8/BOM, quoted commas/quotes and semicolon files. Formula-like text is escaped for spreadsheets and reversibly restored on import. Unknown legacy times stay empty. Stable match IDs identify duplicates; conflicts and the current match ID are skipped. New local daily references are allocated to imported results so deleted numbers are not reused. CSV is a results interchange, not a full backup of active rally/Undo history or unused opponents.

Transfers use gzip plus AES-256-GCM with random IVs and session/direction-bound authentication. Keys are only on the watch and in the QR URL fragment/browser; the relay stores opaque encrypted objects and hashed role tokens. Links expire after ten minutes. Applying or ending deletes the session/payloads; unused expired records/objects are cleaned in bounded batches on subsequent transfer API traffic, rather than by an independent scheduled erasure job. There is no permanent cloud history, account, analytics or diagnostic-upload endpoint. The normal diagnostic QR still decodes directly on the phone.

Limits are 1,000 saved opponent identities, 10,000 completed results, 3 MiB CSV text, 4 MiB expanded transfer content and 1 MiB compressed content. Large histories can be exported in date ranges. The watch's encrypted transfer credentials live only in the activity's memory and are not included in diagnostics or preferences. A process restart requires a new QR.

## New-match setup and person selectors

NEW opens a draft with No name and the last-used BO3/BO5 format. Choosing a format or a saved opponent changes only the draft. Tap Create match to commit; replacing an unfinished score asks for explicit Replace & create match. Back to setup keeps the draft, while Cancel returns to the screen that opened setup. Saved results are kept. The new match stays idle until the first point or Start match.

Person fields, names and the root Opponents entry are purple with the person icon. Secondary navigation is a transparent blue-text row with an arrow. Grey filled controls are reserved for Settings, red for deletion/removal, and Back/Cancel use an outline. During an active or paused match, subpage headers offer a Scoreboard shortcut that preserves session state.

## Keeping the match available

The first scored point starts an ongoing session. **MENU → Current match → Start match** can enable it before the first point; timing still begins at the first point. The session has a quiet Wear OS ongoing indicator and a monochrome ambient scoreboard. Notification permission is requested when starting play, rather than merely opening the app. The session ends when the match finishes or **MENU → Current match → Pause match** is selected. **Resume match** restarts it with the score/timing kept. Explicitly dismissing the task also pauses it.

**Keep screen on** defaults to ON and applies only to an explicitly running match's visible scoreboard. New 0–0 drafts, paused/completed matches and result/settings panels release it. Idle sessions have no ongoing service or ambient observer; the watch can sleep normally. New-match setup waits for the first point, and Undo of a completed match waits for Resume. Undo to 0–0 during a running match keeps that session running. Active play continues across ordinary activity pause/ambient transitions.

After upgrading from v0.6 or earlier, a saved unfinished match waits for **MENU → Current match → Resume match**; its points, Undo, set scores and timing remain intact. New drafts can start with a scoring tap. Use Pause when leaving an unfinished match. Turn Keep screen on OFF during play to reduce display use. Update checks are manual in Settings; there are no launch checks, periodic workers, sensors or app-held CPU wake locks. The diagnostic QR releases its own screen-on flag after one minute or when backgrounded.

Diagnostics include the current whole-watch battery level/charging state and bounded battery snapshots when a session starts, pauses or finishes. No battery polling is added. These readings include all watch activity and do not measure consumption attributable to Squash Tap. Automated checks verify the idle/service/screen policy; real battery consumption and detailed OnePlus ambient transitions remain physical-device checks. The user reports foreground retention working since v0.3.

## Send diagnostics using only the phone

1. Reproduce the unexpected return to the watch face, then reopen Squash Tap promptly.
2. Open **MENU → Settings → Diagnostics → Send to phone**.
3. Scan the watch QR code with the phone camera. Open its link.
4. Choose **Save text file** and share the TXT file with support. **Copy text** or the phone’s file sharing action are alternatives.

The report includes version/device details, permissions and power state, recent activity/ambient/session events, the last captured Java crash and Android’s recent process-exit reasons where available. The QR uses a compressed URL fragment decoded on the phone; diagnostic text is not sent in the HTTP page request. There is no automatic upload. Logs are bounded and may omit older events; this is app diagnostics, not a full watch bug report.

## Phone-only installation and updates

Downloads and guide: https://squash-tap-andrew.endruuu.chatgpt.site

From **v0.5**, open **MENU → Settings → Check for updates → Download & update**. v0.3 enters Settings directly from the header. After v0.13.0 starts, open the grey Settings entry to confirm the version. Future checks use **MENU → Settings → Check for updates**. Keep the existing app installed to preserve its data.

For initial installation or a fallback, download the current watch APK to the Android phone, connect Wear Installer 2 to the watch over Wi-Fi/the phone hotspot, and select **Custom APK**. The watch IP/connection port is different from the temporary pairing port. See [DEPLOYMENT.md](DEPLOYMENT.md) for the tested walkthrough and German OnePlus labels. No PC tools are required for the user.

| APK | Version code | Status |
| --- | --- | --- |
| SquashTap-0.13.0.apk | 14 | Current release: clearer menus, staged setup and consistent Back/swipe routes. |
| SquashTap-0.12.1.apk | 13 | Use manual-set save time when the match start is missing. |
| SquashTap-0.12.0.apk | 12 | Keyboard-free completed-set and whole-match entry. |
| SquashTap-0.11.0.apk | 11 | Explicit phone name assignment, retirement results and private phone browsing/statistics. |
| SquashTap-0.10.0.apk | 10 | Compact result timing and overall match/set/point statistics. |
| SquashTap-0.9.0.apk | 9 | Distinct name selectors and optional opponent selection during new-match setup. |
| SquashTap-0.8.0.apk | 8 | Opponents, filters and encrypted browser CSV transfers. |
| SquashTap-0.7.0.apk | 7 | Explicit sessions, battery-conscious idle and manual update checks. |
| SquashTap-0.6.0.apk | 6 | Start/finish timestamps, duration and B1 migration. |
| SquashTap-0.5.0.apk | 5 | User approved the cleaner navigation/design. |
| SquashTap-0.4.0.apk | 4 | User confirmed installation and QR export; navigation/colour feedback addressed in v0.5. |
| SquashTap-0.3.0.apk | 3 | Oldest retained release; user reports foreground retention working since this version. |

Public release history starts at **v0.3.0**. Earlier experimental APKs are retired from downloads, metadata and checksums. Future builds enforce this policy with `scripts/release_retention.py`; retained APKs stay unchanged.

## Build and verification

Java 17, official Android Build Tools/platform 34 and Python build the app here. Pinned AndroidX libraries provide ambient and ongoing activity support; ZXing creates QR codes. `android-dependencies.json` records exact artifact checksums. JUnit/Hamcrest and the automatically prepared Maven/Robolectric UI toolkit are build-only dependencies. See [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).

**265 JUnit tests pass** against the actual shared scoring, match/history, migration, display-policy, diagnostic-transfer and update/download code. The new APK is compiled, signed with the original key, aligned and inspected. Old APK hashes are retained. The user confirmed QR export and reports foreground retention working. Timing on the physical watch, detailed ambient transitions and battery consumption still need checking. **100 additional Robolectric checks pass** against real Android menus/buttons, window flags, session persistence, service guards and compiled resources. Thirty-five additional Node/Miniflare checks cover browser/native encryption, CSV and relay storage/security. These are headless Android tests, not a Wear OS emulator or physical battery test; see TESTING.md.

The public page, APKs and release feed have no sign-in, Basic Auth or password, following the user’s explicit decision. Scores/results are stored on the watch; explicit browser transfers send encrypted copies. Diagnostics leave it only through the user’s explicit export.

Maintenance: [DEPLOYMENT.md](DEPLOYMENT.md), [ARCHITECTURE.md](ARCHITECTURE.md), [FLOW.md](FLOW.md), [TESTING.md](TESTING.md), [CHANGELOG.md](CHANGELOG.md), [AGENTS.md](AGENTS.md). Historical device evidence is in `DEVICE_TESTS.json`.

The public landing page is a concise feature overview for new users, with download, phone installation and update guidance. Keep project-specific debugging, chat references and internal test narratives in maintenance records rather than the landing page. The standalone diagnostics exporter uses neutral support wording.

## Overall statistics — v0.10

Open **MENU → Statistics** for all saved completed results, from Me’s perspective. It shows matches played/won/lost, win rate and match balance; sets (games) played/won/lost and balance; points played, for/against and point balance. A positive balance means more wins or points for Me. Result filters do not limit these overall totals.

Imported and legacy completed matches count automatically. Unfinished matches do not count, and deleting results removes them from the totals. Undoing a match-winning point removes that result until it is completed again. No additional data, account, network request or stored counter is needed. Empty history shows zero totals and no win-rate estimate.

## End a match early — v0.11

Open **MENU → Current match → Match options → End match early**, choose **I quit** or **Opponent quit**, then confirm. This action is kept off the main scoreboard. The other player wins by retirement. The saved result records the actual completed sets and any unfinished set; no extra points or sets are invented. Retirement counts toward match wins/losses; only finished sets count toward set totals, and all points actually played count. Timing ends on confirmation. The result clearly says who retired.

**Undo retirement** on the current saved result reopens the same match with every rally intact. It waits for Resume; a second ordinary Undo can correct a rally. Deleting that result retains the existing deletion marker. A 0–0 retirement does not invent a first-point start or duration.

v0.11 writes **B3** match books with backward B1/B2 reading. A previous B2 book is privately backed up once as `match_book_v2_backup`; the B1 backup is preserved. Normal active games retain M1 encoding; only retired matches need M2. Do not downgrade after migration. CSV remains schema 1 for ordinary exports; a file containing retirements uses schema 2 with the extra `retired_player` column. Both formats import in v0.11.

## Private phone results and player statistics — v0.11

Use **MENU → Results → Options → View results on phone → Show phone QR** for your complete saved history. Scan the QR to open the encrypted ten-minute page. Results supports combined opponent/date filters, pagination, expandable timing/set details and CSV download. Statistics shows the overall win/loss record across all players, sets/points, recent W/L history and each opponent’s balance. Tap an opponent or a recent result to browse that history. Statistics uses every result in this transfer, independently of browsing filters; a deliberately filtered export is clearly labelled as a subset.

Adding or restoring saved names on the phone **never selects a match opponent**. To assign someone, explicitly expand Change a match opponent, enable its checkbox and select the person. The page identifies whether this is the current or last completed match. The watch preview highlights any assignment change. v0.11 also ignores reassignment without an explicit boolean opt-in from older phone pages. Renaming an existing person still updates their linked display name, as intended.

To correct an old accidental assignment, open that match result on the watch, tap its purple Opponent field and choose the correct person or No name. The app cannot infer who actually played a historical match. Scores and timing are kept. Start a fresh QR session after updating the phone page; no account or permanent cloud history is added, and viewing/filtering/statistics makes no background requests.

## Manual scores without a keyboard — v0.12

For a current set, open **MENU → Current match → Enter set score**. For a separate historical result, use **MENU → Results → Add past match**. The main scoreboard stays unchanged.

- **Enter set score** records the current match’s next completed set. Choose Me won or Opponent won, then use a losing-score wheel, large +/− controls or blue score presets. The winning score adjusts to PAR-11/win-by-two, including deuce (for example 12–10). Me is always the first displayed score. Review and Save set score; replacing an unfinished current set explicitly identifies its previous score. Earlier sets are kept.
- **Score next set live** moves to the next set and starts/resumes live scoring. **Enter next set score** lets you keep recording only completed sets. The final match-winning set saves the result automatically.
- **Add past match** adds an independent completed result to history. Select BO3/BO5, enter each set, optionally pick a saved opponent and choose Today, Yesterday or a date using day/month/year wheels. Tap a score card to edit it or remove the last draft set before saving. An unfinished/invalid match cannot be saved. Your existing live match, opponent, timing, Undo and session are kept.

These controls need no watch keyboard. Opponent selection uses saved names or No name; add names separately through the phone page if needed. Entry is offline and all data stays local until an explicit transfer.

Entered set totals do not contain an invented rally sequence. Undo therefore asks before removing an entire entered set; genuine live points still undo individually. From v0.12.1, saving a completed set uses the current watch date/time and timezone as the match start if none is recorded. An existing start is preserved across later entries, Undo, live scoring and reopening. Cancelling entry sets nothing. A manually submitted final set still leaves its actual finish and duration Not recorded; later live scoring can record its finish and calculate duration from the saved start. Independent Add past match entries keep their chosen date with start/end times unknown.

v0.12 writes **B4** and reads B1/B2/B3. Existing B3 is privately backed up once as `match_book_v3_backup`, alongside earlier recovery copies. Normal M1 and retirement-only M2 encodings remain; a current match with entered set totals uses M3, with score-only `2|me|opponent` sets. CSV/phone transfer formats, opponent identities, statistics, daily references and deletion markers are preserved. Do not downgrade after migration.

## Navigation — v0.13

Current match contains the relevant session action, Enter set score, Match details and Match options. Finished matches show View result and New match instead. Results has compact Filters/Options controls and Add past match; Options holds phone viewing, CSV import/export and Delete all results. Opponents offers Edit names on phone. The phone QR opens the matching Results/Opponents/Import tab through the same encrypted temporary session.

Each page has one named parent Back/Cancel action. Hardware Back and native swipe dismissal use that same route. Returning from a result, opponent selection or delete cancellation preserves the result list filter, page and scroll position. Results opened from Statistics returns there. A phone request that finishes after leaving its page does not reopen a menu over scoring.

## Public landing page and private links

The landing page lists the current watch APK only, alongside concise features, phone-assisted installation and QR privacy information. It has no generic transfer launch link or old-version download list. All retained APK files (v0.3 onward), builds.json and checksums remain available and unchanged for recovery. Hiding links does not remove binaries.

Opening transfer.html without a watch QR shows instructions and no match data. Each transfer uses independent random ID/key/owner/guest secrets; tokens are checked against that session and encrypted content is bound to its ID/direction. Other users cannot use their own QR to open this transfer. Anyone with the full active QR/link can access that transfer, so keep it private. Access expires after ten minutes; downloaded CSV files remain on the receiving phone.
