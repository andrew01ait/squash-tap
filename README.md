# Keep Score

A standalone, offline racket-sport scorekeeper for Wear OS. Current watch release: **v0.23.4**, Android version code **43**. No account is required; scores and saved history live on the watch.

[Download the watch app](https://squash-tap-andrew.endruuu.chatgpt.site/) · [Source for this release](https://squash-tap-andrew.endruuu.chatgpt.site/KeepScore-source.zip) · [GitHub](https://github.com/andrew01ait/squash-tap) · [Install using your phone](INSTALLATION.md)

A separate **0.17.0-play.1/code 23** candidate prepares Google Play distribution with API 35, AAB output and Play-managed updates. It is not the public watch release or a Play Store launch. See [Google Play preparation and remaining gates](PLAY_RELEASE.md).

## Referee, spectator watches and phone results

Open **Live sharing & referee** below the six sports under **Home → Sports**, from a sport menu or **Current match**. During play, tap the sport name and **Menu** at the top of the scoreboard. Choose **Referee on phone** to let a referee scan the private QR. The watch shows the score and offers **Save to my results** after the referee confirms the finish. Open the link online before play. The referee can record rallies, undo a mistake and finish offline. If the browser suspends uploads, reopen the same link on the same phone after reconnecting. The page shows when the score has been uploaded.

To cancel while a referee is scoring, tap **Match menu** on the host watch. **Cancel match** is the first action. **Discard match** ends the session for the referee and all connected watches without saving a result. **Keep playing** returns to the menu. After a discard, Back or swipe opens Choose sport and then Home.

Open **Live sharing & referee → Share with watches → Pair watches**. Show the ten-character code on the host watch, scan the invitation QR with a phone to display it, or pair two compatible watches via NFC. On the joining watch, open **Live sharing & referee → Join with watch code** and enter the two groups of five characters. Both modes use the same room. Followers can save the result once and choose the player whose perspective to record. Use the same ten-character code on every player watch. On the host, **Share with watches → Connected devices** lists each watch and referee browser with a short identifier and its last contact. Match that identifier with the one on the device, select it and confirm **Remove device**. The other devices stay connected and the code remains valid. New rooms with device management require v0.20.0 on the host and player watches. Disabling new joins stops new watches from joining. Already joined watches can keep following. Open the app to catch up. A room expires after 14 days, with a fresh 14-day window when the final reaches the service. A confirmed result already received stays on that watch until you decide what to do.

**Tap to join or open a page.** With an invitation open in **Share with watches → Pair watches**, select **Pair a watch via NFC** on the host and **Join via NFC** on a second watch. Touch the watches together. The second watch follows the score and can save the finish. The same ten-character code works on watches that need manual entry. To open the audience, referee or journal page on an unlocked NFC phone, choose **Share page via NFC** on the QR screen and hold it against the watch until the watch shows **Link read**. If the phone reports an empty tag, move it away before trying again or scan the same QR. Leave the share screen open for one minute to see whether an unsuccessful tap reached Keep Score. The available actions depend on the NFC modes each watch supports.

**Web scores and match cards.** A green check marks the winner of each completed game or set in live and referee scorecards, the journal and score entry. Open a finished match in the journal and choose **Download result card** or **Share result card** for a PNG with the final score, teams and game-by-game breakdown.

**Enter or correct a result on a phone or tablet.** On the watch choose **Results → Options → View results on phone → Show phone QR** and scan it. In the browser choose **Add result**, or expand a saved match and choose **Edit result**. Enter the players and scores for each game or set. Choose **Stage result → Send changes**, then **Receive changes → Apply changes** on the watch. A correction keeps the original match in your history. Date-only entries can also be moved to another day.

**Results → Options → View results on phone** opens a private match journal with sport, player, role, date and outcome filters. The insights include winning streaks, deciding games or sets, known durations and separate partner and opponent records. **Players → Edit names on phone** opens the roster editor. Language follows the watch or browser and can be set independently in watch Settings or on each web page. Available languages are English (fallback), German, French and Spanish.

**Watch navigation.** Open **Players** directly on the sports screen or beside **Current match** in a sport menu to manage saved names. In **Current match → Match setup**, select the opponent or doubles team for this match. **Score**, **Sharing** and **End match** group the other actions. A shared scoreboard opens its own match menu with set scores, sharing and Settings. On every watch use **Settings → Language**. In doubles, setup places your partner and opponents under their respective teams.

## Features

- Choose alphabetically: Badminton, Padel, Pickleball, Squash, Table tennis or Tennis. Results and statistics are separated by sport; eligible sports have singles/doubles filters.
- Use one shared Players catalog for opponents and partners. Small sport icons indicate saved results in any role; renames keep stable identities, and deleting/reopening results updates the icons.
- Read the set or game tally and the current period above the rally scores. Tennis and padel show games within the current set. Other sports show the last completed score when available. **Current match → Match details** lists all entered scores, newest first.
- Keep two large scoring cards for singles or teams. Tennis/padel games advance automatically; select optional first-server choices for serving guidance. A bright independent serving badge and score-card outline stay visible with missing or long names; doubles use stable role labels. No first/second serve, let or stroke recording.
- Choose both old/new badminton, padel and pickleball formats. Each match retains its own rules. See [Sports and scoring](RULES.md) for exact presets, service guidance and scope.

- Tap the left **Me** or right **Opponent** card to score a rally. Squash PAR-11 requires a two-point lead; Undo corrects mistakes.
- Choose the supported match length for each sport, keep every set score, and optionally choose a saved opponent. New-match choices stay in a draft until Create match.
- Save completed matches automatically, or use Match options → Save retirement result to record an early win/loss. Current match → Cancel match → Discard match quits without saving scores or a statistics entry. Saved history and shared names are kept.
- Enter a completed set for the current match or add an independent past match using score wheels, large controls and saved names.
- Browse history with combined opponent/date filters: **10 matches per watch page**, newest first. Returning from a result preserves the page, filters and scroll position. Reset all filters clears player, dates and mode for the selected sport and returns to page one. It appears at the top of Result filters and directly in filtered Results; the phone provides the same action.
- See match wins/losses, game/set totals and known points for the selected sport/mode. The phone view also provides per-opponent balances and recent W/L history.
- View results, edit names and import/export CSV through an optional encrypted, ten-minute QR session in a phone browser. Phone results use 25 matches per page.
- In Settings, check for watch updates or send a diagnostic QR to a phone. During an update, the watch shows the percentage downloaded and displays status changes through verification and installer handoff. The compact QR includes current watch status and gives the latest NFC sharing event priority over routine activity changes. Copy details on the watch includes additional system and crash information. An explicit active match controls the ongoing session, ambient scoreboard and screen retention.

- After the final point, read the result and tap **New match** to choose players, or **Quick rematch** to play again with the same rules and available players. Save up to 24 named favourite formats.
- Choose Classic, Neon Lime or high-contrast Sunlight scoreboard colours from a preview list. Brightness follows watch system settings; the app has no light-sensor loop or forced brightness.
- Hear the score after each rally or Undo with **Settings → Feedback → Announce score**, below Vibrations. The watch uses first names and an installed voice in the app language at media volume. Tennis and padel calls identify the game winner and set leader. Squash and badminton call game and match points. Traditional pickleball distinguishes a team change from the second server.
- Choose point/milestone/off vibrations and optional non-blocking change-ends/interval prompts. Add a read-only match tile or score complication from the watch’s system pickers.
- Download a complete local backup or share result-card PNGs.
- Stream live results to a read-only audience website when internet is available, including matching-player head-to-head and the last meeting. See the [live score streaming guide and preview](https://squash-tap-andrew.endruuu.chatgpt.site/#live-scores).

## Where to find things

The Keep Score logo and name appear above the Home tiles and on the Wear OS Tile. Home offers New match (green), Results for the last selected sport (teal), Players (violet), Sports (orange) and Settings (grey). Each destination keeps its colour throughout the menus. New match opens sport selection then setup. A prepared or running match opens its scoreboard. A paused match opens Resume. A saved finish opens its result until you choose Mark match as done or leave it untouched for three hours. Every sport has a distinct icon and accent colour, also shown in menus and on the scoreboard. There is one current match across the app. Other sports are locked while an unfinished match exists, including paused or prepared matches; finish or cancel it first. Shared Players and Settings show the Keep Score app name, remain accessible and return to their actual caller. Sport-specific screens retain their sport name and icon.

| Menu | Purpose |
| --- | --- |
| New match | First action on a saved finish and in idle menus. Opens setup for the chosen sport. |
| Current match | Start/Pause/Resume, Enter set score, Match details, Live scoreboard and Match options. |
| Favourite formats | Reuse sport-specific scoring presets saved from New match. |
| Results | The newest matching scorecard comes first. Filters and Options follow. Options opens past-match entry, the phone journal, CSV and bulk deletion. |
| Statistics | Overall results across all saved completed matches, independent of history filters. |
| Players | Filter by name and optionally show only people played in the last 30 calendar days. Sort A–Z, Z–A or by most recent activity. Add, rename or remove a player on the watch or edit names on the phone. |
| Settings | Full backup/restore, colour schemes, system brightness help, vibrations, spoken scores, reminders, tile/complication help, updates and diagnostics. |

A green button starts a new match. Blue buttons control the current match. Player rows are purple, Settings grey, destructive actions red and Back/Cancel outlined. Tap a menu heading to expand the route. Beside the badge, one short horizontal bar marks each menu level. A screen reader announces the level. Menu sections separate sport actions from app-wide controls. Mode, scoring rules, deuce, deciding set, match length, history mode and screen policy open explicit selection lists with the current choice marked. Buttons, hardware Back and the native swipe-dismiss callback share the same parent destination. Browse the complete [watch menu map](public/watch-menus.html) or read [FLOW.md](FLOW.md).

## Timing and manual entry

Timing starts on the first accepted live point. Start match enables availability before a point without inventing a start time. If no start exists when a valid manual set is saved, that save records the current watch date/time/zone as the start. Existing starts are preserved.

A live winning point or confirmed retirement records the finish. A manually entered match finish is unknown; independent historical matches remain date-only. Duration includes pauses and set breaks when both times are known. Same-day details display the date once and short Start/End times; overnight matches also show the finish date. Legacy unknown times stay unknown.

Choose **Current match → Enter set score** to change the current match. Choose **Results → Options → Add past match** to append separate history without changing current points, opponent, timing or session. Entered totals do not invent rally order, and Undo of an entered set asks before removing the whole set.

## Players, history and statistics

The Last 30 days filter includes today and the previous 29 calendar days. It uses the recorded playing day of completed matches across all sports. Clear the tick to restore the entire roster. Most recent activity counts matches in any sport, including doubles partners and both opponents. An active match comes first. Saved results use their playing date, followed by recorded start time and daily match number. A backdated import keeps its place. Unplayed names follow alphabetically. The phone uses the history sent to that phone session.

Names are optional. Players → Your name sets your own display name (blank means Me), independently of the opponent catalog. Your name appears on the watch, private phone view, result-card image and live scoreboard. Adding a name on the phone never assigns it to the last/current match. Match reassignment needs a separate explicit opt-in and watch review. Renames follow stable opponent IDs. Removing a name archives it from future selection while retaining its identity in old results and filters.

Deleting a result requires confirmation and never reuses its daily match number. Deleting all history keeps the current scoreboard. Undo/re-completion preserves the original reference and cannot revive a deliberately deleted current result. Retired players lose the match; actual sets/points alone enter their corresponding totals.

## Live result streaming

Open **Live sharing & referee → Audience live link → Start live sharing** on the watch. Scan the audience QR with your phone and use the website's share icon to send the audience link. Spectators follow points, games/sets and serving cues in their browser without an account or additional app. The page supports portrait/landscape, colour choices and fullscreen.

Matching saved opponents in the same sport/mode include a head-to-head balance and latest previous result. Doubles require the same partner and opposing pair. The link shares only the current match and this bounded summary, not your entire history. Anyone with the complete active audience link can view it.

Internet is needed for audience updates; watch scoring remains offline. The latest score retries after reconnecting while the app process remains available. Live access lasts up to eight hours during play; completed results show the winner and actual game/set scores for ten minutes after completion. Stop sharing can close access earlier when the watch is online. Audience sharing is separate from phone transfers.

## Phone transfers and privacy

On the watch, open **Results → Options → View results on phone → Show phone QR**. Scan it with the phone camera. Both devices need internet for this optional transfer; playing and recording scores remain offline. A companion phone app is unnecessary after initial APK installation.

Each transfer has independent random secrets. The QR carries the decryption key in its URL fragment; the relay stores encrypted payloads and hashed access capabilities. Anyone holding the complete active link can open that transfer, so keep it private. There is no public history listing or permanent account-based cloud history.

Access expires after ten minutes. Ending/applying requests deletion immediately; unused expired ciphertext is physically cleaned during later API traffic, not at a guaranteed scheduled erasure time. Downloaded CSV files remain on the phone until their owner removes them. CSV schemas 1/2 remain readable as squash; schema 3 preserves sport, rules, team identities, seven-game histories and known point/tie-break details. Older watches must update before importing schema 3. CSV is a completed-results interchange, not a complete backup of active rallies, Undo or unused names. See [ARCHITECTURE.md](ARCHITECTURE.md) for limits and the data flow.

## Keeping a match available

The first point or Start/Resume enables an explicit match session. Pause, completion, replacement or task dismissal stops it. Keep screen on applies only to the visible scoreboard of an active match; idle drafts, paused/completed matches and panels allow normal display sleep. Ordinary activity pause/ambient transitions preserve an explicitly running match.

Updates are requested manually in Settings. There is no background update polling, health/sensor tracking or app-held CPU wake lock. Diagnostics include whole-watch battery snapshots, not app-attributed energy measurements. Physical battery and gesture acceptance remain separate from automated checks.

## Documentation

| Guide | Audience and contents |
| --- | --- |
| [INSTALLATION.md](INSTALLATION.md) | Phone-only installation, German OnePlus labels, direct updates and diagnostics. |
| [RULES.md](RULES.md) | Sport presets, old/new rules, optional serving and scorekeeper limits. |
| [FLOW.md](FLOW.md) | Current menu routes, staged choices, returns and user actions. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Native components, data formats/migrations, power policy, relay and update verification. |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Maintainer build, signing, retained APKs, website publication and release procedure. |
| [TESTING.md](TESTING.md) | Reproducible checks, recorded release evidence and physical-device acceptance. |
| [VERSIONING.md](VERSIONING.md) | Watch versus website versions, compatibility, rollback and documentation-only changes. |
| [PLAY_RELEASE.md](PLAY_RELEASE.md) | Play candidate, reproducible bundle verification, account/privacy/testing gates and listing drafts. |
| [GITHUB.md](GITHUB.md) | Source layout, public/internal differences, contribution/sync procedure and fork prerequisites. |
| [AGENTS.md](AGENTS.md) | Maintenance constraints for future coding work. |
| [CHANGELOG.md](CHANGELOG.md) | Release and documentation history. |

The v0.23.4 direct release passed **349 core + 290 watch UI + 112 web/relay tests = 751**. The v0.23.3 direct release passed **349 core + 288 watch UI + 110 web/relay tests = 747**. The v0.23.2 direct release passed **349 core + 287 watch UI + 106 web/relay tests = 742**. The v0.23.1 direct release passed **349 core + 284 watch UI + 105 web/relay tests = 738**. The v0.23.0 direct release passed **348 core + 274 watch UI + 104 web/relay tests = 726**. The v0.22.1 direct release passed **345 core + 270 watch UI + 103 web/relay tests = 718**. The v0.22.0 direct release passed **345 core + 256 watch UI + 103 web/relay tests = 704**. The v0.21.6 direct release passed **344 core + 246 watch UI + 103 web/relay tests = 693**. The v0.21.5 direct release passed **344 core + 243 watch UI + 103 web/relay tests = 690**. The v0.21.4 direct release passed **343 core + 243 watch UI + 103 web/relay tests = 689**. The v0.21.3 direct release passed **343 core + 242 watch UI + 103 web/relay tests = 688**. The v0.21.2 direct release passed **340 core + 241 watch UI + 101 web/relay tests = 682**. The v0.21.1 direct release passed **340 core + 239 watch UI + 101 web/relay tests = 680**. The v0.21.0 direct release passed **340 core + 236 watch UI + 101 web/relay tests = 677**. The v0.20.2 direct release passed **340 core + 231 watch UI + 98 web/relay tests = 669**. The v0.20.1 direct release passed **340 core + 226 watch UI + 98 web/relay tests = 664**. The v0.20.0 direct release passed **339 core + 222 watch UI + 98 web/relay tests = 659**. The v0.19.0 direct release passed **339 core + 215 watch UI + 95 web/relay tests = 649**. The v0.18.1 direct release passed **335 core + 214 watch UI + 91 web/relay tests = 640**. The previous v0.18.0 release passed **335 core + 206 watch UI + 90 web/relay tests = 631**. The historical v0.16.4 suite passed **331 core + 191 Android UI + 64 web/relay checks = 586**. The historical v0.13 release suite was **265 core + 100 Android UI + 35 web/relay checks = 400**. This is release evidence, not a claim that every documentation update reruns or physically validates the watch. See [TESTING.md](TESTING.md) for current verification.

Full backups use the local export date and application name: `YYYYMMDD_ApplicationName_Description.json`, for example `20261111_KeepScore_Backup.json`. Completed-result interchange remains CSV; full backup JSON also preserves current rallies and Undo.

[Imprint and contact](https://squash-tap-andrew.endruuu.chatgpt.site/imprint) identifies Andrew Lindley. Settings → About me on the watch includes an introduction and a QR link to this page.

The landing page offers keyboard-accessible previews of all six scoreboards, supported formats, native menu/name examples, installation/update help and transfer privacy. Its dedicated Live scores section has a navigation/hero link, three setup steps and an illustrative audience scoreboard. Preview images and sample scores use synthetic data, never real player exports.

## Complete backup and live audience

Settings → Backup & restore → Create or restore backup opens an encrypted ten-minute phone link. Download `YYYYMMDD_KeepScore_Backup.json` or choose a saved backup and send it. Receive changes on the watch, review, then explicitly Replace data & restore. This replaces all sport history, players, own name, favourites, colour/settings and the current match including Undo. It keeps the previous data as a downloadable recovery copy; restored unfinished matches are paused. Network keys, permissions and diagnostic logs are excluded. JSON backups are private user files and must be kept safely.

Live sharing & referee → Audience live link → Start live sharing creates a separate read-only audience QR link for this match’s score, names, rules, serving cue and a bounded previous-meeting summary when saved players match. Share the complete audience link; the phone-transfer link is private. Live play lasts up to eight hours. Completion changes the link to a ten-minute final-result window showing the winner and actual game/set scores; repeated updates do not extend it. The share and copy controls use labelled icons. The watch pushes score/session changes and retries the latest queued score after connectivity returns; scoring never waits for internet. Visible audience pages refresh every three seconds (ten after completion) and pause requests when hidden. Stop sharing revokes access when online; offline revocation can fail and the link remains until expiry. Final results and previous-meeting details clear when access expires. Undo before expiry reopens live scoring under the original live deadline; replacement of an unfinished match revokes sharing. A confirmed discard replaces the shared score/names with an anonymous “Match discarded” notice until link expiry, with no saved watch result. If offline, that notice is queued in memory and retried on reconnect. Task dismissal revokes unfinished sharing; completed results retain their final window. Queued final results can retry on reconnect after starting the next match while the process survives. Expired live rows are physically cleaned on later link creation, not by an exact-time erasure job.

A completed result offers Share result card, opening the private phone view with Save/Share result-card image actions. PNGs contain only the selected match. Wear OS tile/face displays are event updated, with no periodic watch polling; tapping opens the match and preserves pause.

The audience page adapts to portrait phones and short landscape screens. Portrait keeps two large aligned cards; landscape places match details and controls alongside them. Full team names wrap, serving has its own badge, and viewers independently choose Classic/Neon Lime/Sunlight. Browser QA covers actual 393×852 and 852×393 CSS viewports with synthetic doubles names, aligned scores, all controls reachable and no horizontal overflow. These are browser layout checks, not physical phone acceptance.

Settings groups Display, Feedback, Data, Watch shortcuts and App controls. Vibration, spoken score, reminder, screen and palette selectors save in place and show the selected tick until Back to Settings; button, hardware Back and swipe share that route. Selecting an already selected option does not navigate. Live sharing opens its audience QR automatically when ready while the waiting page remains visible; moving elsewhere prevents a late QR interruption.

Head-to-head counts previous completed matches in the same sport/mode, excluding the current match. Singles use the saved opponent identity. Doubles require the same partner and opposing pair, allowing the opponents to swap positions. Unknown/unnamed players never guess a match. The latest meeting uses its recorded date/time, with actual scores and retirement outcome. No full history or saved-player identifiers are published.
