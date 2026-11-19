# Navigation and match flows — v0.21.2

The separate Play preparation candidate retains these scoring/navigation flows. Its Settings → Check for updates opens a Google Play explanation, Open Google Play and Back to Settings. All website update/download/install/setup entry points route there in that build; a missing Store produces an actionable message and keeps the match. The public direct-APK release keeps its existing updater. See [PLAY_RELEASE.md](PLAY_RELEASE.md).

## NFC watch and browser handover

| From | Action | Result |
| --- | --- | --- |
| Host watch with an active watch invitation | Choose **Pair a watch via NFC**. On the receiving watch open **Live sharing & referee → Join via NFC** and bring the watches together. | The receiver uses the shared watch code and joins as a read-only follower. Each additional watch can join the same match. |
| Host already broadcasting to browsers | Open **Audience live link → Invite a watch**. | The host opens watch sharing for this match. A referee can keep scoring while other watches join. |
| Audience, referee or private transfer QR | Choose **Share page via NFC** and bring an unlocked NFC phone close. | The phone offers the same page and role as the displayed QR. **Back to QR** returns to the same code. |

Keep the devices together until the watch confirms the link or invitation was read. If the phone reports an empty tag, move it away, return to the QR and retry or scan it with the camera. If Keep Score is still waiting, leave the NFC page open to see whether a reader command reached the app. Settings → Diagnostics contains the command count and last stage. Each NFC attempt lasts up to one minute while its page is in the foreground. Returning, leaving the app or timing out ends the attempt. Sending requires host card emulation on the host. Watch reception requires NFC reader mode on the joining watch. Manual watch codes and optical QR work independently of these modes.

## Sport entry and scope

The launcher opens Choose sport with distinct icons/colours for Squash, Tennis, Badminton, Padel, Table tennis and Pickleball, with a direct Players row immediately after the sports, followed by Sharing and App. An unfinished current match locks every other sport, including when paused or explicitly created but unplayed. The selector explains the lock and disables the other sport rows; stale selection/create callbacks also enforce it. Finish or cancel the match to unlock selection. A completed match permits another sport. Close app exits without silently opening another sport’s scoreboard. Each sport remembers its new-match rules and length.

Results and Statistics use the chosen sport. Eligible sports offer All modes / Singles / Doubles. Result player filters include all team roles; date/player filters do not narrow Statistics. Delete all results deletes the selected sport only and preserves other sports, current state, daily counters and deletion markers. Phone Results/Statistics have their own shared sport/mode selectors; the Players catalog is global.

New match opens explicit, ticked lists for mode, scoring system, deuce, deciding set and match length. Opening a picker, Cancel, hardware Back and swipe leave the draft unchanged. Selecting one option returns to the previous setup scroll position. Choices appear before help text, and selecting the current choice preserves serving data. New match stages sport-specific rules, length, optional opponent/partner/opponent-2 and optional first-server choices. Padel stays doubles; squash stays singles. Duplicate people in different slots are rejected. Tennis/padel games advance automatically; NEXT remains explicit between sets. Rules are locked to the match; changing new-match defaults does not reinterpret old results. [RULES.md](RULES.md) lists formats and service choices.

Players shows small vector sport badges only for associated saved results as partner or opponent. No-result players have no badges. Delete/Undo, import and role reassignment are reflected on the next render. Names remain optional. Removing a player archives selection, and renaming follows their stable ID across sports.

## Watch menus by match stage

| Where you are | What to use | What Back does |
| --- | --- | --- |
| Launcher | Select one of the six sports. An unfinished match has its own shortcut. Players follows the last sport. Sharing and app settings each have a section below it. | Close app is the explicit launcher exit. |
| Sport menu before a match | New match opens the draft. Choose rules, singles or doubles as supported, length, players and service order. Players opens the shared roster directly below the primary match action. The sport menu also opens saved formats, rules, history and statistics. | The sport menu returns to the launcher when entered there. Cancel in setup returns to the caller with the previous match intact. |
| Ready, playing or paused | Current match puts Start, Pause or Resume first. Use the scoreboard for points, Undo and advancing a finished set. Score groups the tally, set entry and match details. Match setup opens the opponent or team selector and match options. Sharing and End match have their own sections. | The match shortcut returns to its entry point. Back from a player picker or ending confirmation returns to the menu that opened it and preserves the score and pause state. |
| A referee scores or a watch follows | The scoreboard is read-only. The host can show the referee QR, invite other player watches and manage connected devices. A follower can inspect set scores and its device identifier. | Back from sharing or set scores opened in Shared match returns to that menu. Back from Shared match returns to the scoreboard. Leaving the match requires a separate confirmation. |
| Finished match | View the result, start a new match, share the saved result card or inspect set scores. The current-match menu retains existing sharing. If result storage was full, Save match result retries saving after space has been freed. Saved current results offer Undo and Quick rematch. | Result details return to the view that opened them. |
| History and statistics | Results contains filters, manual past-match entry and options for the phone journal, CSV and bulk deletion. Statistics uses the selected sport. Players and Settings are shared destinations. | Back retains the chosen sport, results page and filters. Settings selectors retain their parent and scroll position. |

Mixed menus use translated headings announced by the screen reader. The sport menu groups match actions and Players, match history, sport-specific tools and app settings. Current match leads with the next session or result action. Filters separate match and date choices. Doubles setup and editing put the partner on your team and both opponents on theirs. Sharing separates hosting from joining, then invitations and devices from connection management. A device group appears only when it has members. Single-choice lists mark the selected option with a tick. Result tools use full-width rows. QR screens reserve their centre for the code and provide a native outlined Back button. All menu pages use the same scrollable surface, with 48 dp minimum button and input heights.

The named Back action, system Back and a swipe dismiss one watch panel at a time. Cancelling a pending join returns to code entry. A delayed room reply cannot attach the cancelled match.

## Root and priorities

The scoreboard stays focused on Me/Opponent scoring, Undo and Next/New. Its header opens Menu within the selected sport:

| Destination | Purpose | Style |
| --- | --- | --- |
| Current match | Live session, set entry and current scores | Filled blue |
| Players | Shared player list, accessible directly below the match action | Purple person row |
| Live sharing & referee | Audience QR, phone referee, share with watches, join by watch code | Direct navigation row |
| Results | Saved history, filters, past matches, CSV and phone viewing | Blue navigation row |
| Favourite formats | Choose a saved sport preset or manage favourites in setup | Blue navigation row |
| Statistics | All-result match/set/point balances | Blue navigation row |
| Change sport | Return to the six-sport selector | Blue navigation row |
| Scoring rules | Sport presets, service guidance and limits | Blue navigation row |
| Settings | Version, updates, screen/notification options and diagnostics | Grey |

The sport Menu returns to its own current scoreboard when available; an idle sport Menu returns to Choose sport. A sport icon/name badge identifies the scope. App-wide Players/Settings and their subpages use a Keep Score badge and return to their originating Menu or sport selector. Every subpage has one named outlined Back/Cancel action. Hardware Back and native right-swipe dismissal invoke that same return. During active or resumable play, compatible subpages have a Scoreboard shortcut for the actual match sport; it changes no session state. Dialog replacement, backgrounding and ambient transitions do not act as a user Back.

## Current match

| State | First action | Other actions |
| --- | --- | --- |
| Fresh | Start match | Score, Match setup, Sharing, End match |
| Playing | Pause match | Score, Match setup, Sharing, End match |
| Paused / waiting to resume | Resume match | Score, Match setup, Sharing, End match |
| Completed | View result if saved, Save match result if pending | New match, Share result card if saved, Match details, existing sharing |

The summary shows the singles players or both doubles teams, games or sets won, current points during play, and match length. Match setup opens the opponent or team picker directly. End match offers separate retirement and discard flows. Each confirmation returns to its caller when cancelled. Match details and the read-only Game or set scores view show a large tally and individual scores, newest first, with the current period identified. The live scoreboard shows the period, tally, and either games in the current set or the most recent completed score. Match options groups Players/Opponent, Serving indicator and Match length under Setup & players, with New match and Save retirement result under Finish or replace. Cancel match is available directly in Current match and in Match options. The frozen scoring rules are summarised separately from adjustable match length. These options never contain Settings. Completed matches group the result, match summary, available sharing and the next match. If result storage is full, the finished scoreboard stays available. After freeing space, Save match result retries from Current match or Match details.

Match format changes an unfinished current match in place, preserving UUID, points, sets, timing and Undo. BO5 → BO3 is rejected after either player already has two set wins. Changes return to Match options. Screen-on policy and history Singles/Doubles filters also use explicit lists rather than cycling through values. Confirmation questions omit the Scoreboard shortcut to keep the question and explicit confirm/cancel actions clear.

## New-match setup

NEW, Match options → New match or a current result → New match opens a draft with No name and the last-used format. Opponent selection is optional and purple. Match length and scoring selections change only the draft. Create match commits a fresh UUID; if an unfinished score would be replaced, Replace & create match must be confirmed. Back to setup retains the draft; Cancel returns to its original caller. Old results remain. Preparing a match does not activate an ongoing session.

## Live scoring, timing and saving

A first accepted point starts play and timing. Set wins retain their score and wait for explicit NEXT. The winning match point saves the dated result and daily number, releases the active session and opens the result. Undo can reopen the current completed match, removing its stale result while retaining the original start/reference. A later completion reuses that reference; deletion never permits number reuse or revives a deleted current result.

Timing includes pauses/set breaks. Start is the result heading; details show the date once with short Start/End times, an overnight end date when needed and elapsed duration. Old untimed records remain unknown. From v0.12.1, an explicitly saved manual set supplies the current date/time/zone as a missing start. Existing starts survive subsequent entries, Undo and reopening. A manually submitted finish remains unknown; a later real live winning point can record its finish.

## Enter a completed set

Current match → Enter set score → choose Me/Opponent won → losing-score wheel / +/− / presets → Review set score → Save set score. The review identifies replacement of any current partial score and explains the missing-start fallback. Cancel changes neither score nor time. Earlier sets are retained.

After saving, Score next set live advances and activates/resumes play. Enter next set score repeats manual capture. A match-winning set saves the result. Undo of entered totals asks before removing the whole set because no rally order was captured. Genuine live rallies retain ordinary point-by-point Undo.

## Record a separate past match

Results → Add past match → choose BO3/BO5, optional saved name and Today/Yesterday/date wheels → enter/edit valid sets → Save match result. This appends independent history without replacing current score/person/timing/Undo/session. Date and score controls need no keyboard. Cancel adds nothing; repeated Save callbacks add only once. Historical start/finish stay unknown. B4/B5 and M3/M4 score-only storage, B1/B2/B3/B4 and O1 recovery backups, counters and deletion markers are preserved.

## Results and statistics

Results presents compact Filters/Options controls, Add past match and blue/red result cards. The watch shows at most 10 results per page with Page X of Y and Older/Newer results controls; the phone shows 25 per page. Filters apply across the complete selected history before pagination. Filters combine opponent/unnamed and inclusive dates. Result details, opponent selection and delete cancellation return to the same filtered page and restore its scroll position. Results opened from Statistics returns there; the root shortcut returns to Menu.

Options contains View results on phone, Export CSV, Import CSV and red Delete all results. Export CSV explicitly chooses All results or Filtered results. A single result has a red Delete this result action and confirmation. Current-result correction retains Undo safeguards. Bulk deletion keeps the active scoreboard and durable number counters.

Statistics derives match W/L, game/set and known point totals from saved results for the selected sport/mode, independently of list filters. Completed/imported/legacy results count; unfinished/deleted/Undo-reopened results do not. Retirement affects match W/L without inventing set wins or points. No aggregate counters or background job are stored.

## Ending early

Current match → Match options → Save retirement result → I quit / Opponent quit → confirmation → save retirement result with actual scores → release the active session. Cancellation preserves the match. Undo retirement reopens all rallies and waits for Resume. Deletion must not revive the result.

## Cancel without recording

Current match → Cancel match → Discard match removes only the unfinished current match, without a result, retirement, win/loss, daily reference or statistics entry. Keep playing, hardware Back and swipe preserve the match/session. A successful discard commits a fresh empty current state, removes only the abandoned match’s role assignments, clears active/pause/prepared state, stops the service and returns to the sport Menu. Shared names, saved results, durable counters and historical identities remain. Undo cannot recover the discarded rallies. Other sports become selectable. Finished results use their existing confirmed deletion flow instead.


## Names

Players centrally adds/renames/removes catalog entries. Remove from opponent list archives future selection while past results retain identity/filterability. Match options and result details open optional saved-name selection. Picker cancellation/save returns to its actual caller, including the current result page. NEW stages selection for the new UUID only. Phone catalog additions/restorations never automatically assign a match opponent; reassignment requires the existing separate explicit opt-in and review.

## Phone links, CSV and privacy

View results on phone, Edit names on phone and Import CSV create/reuse the same encrypted ten-minute QR transfer. The QR's local view fragment selects Results, Opponents or Import after decryption; old/unknown values default to Results. It changes no authorization, snapshot data or encryption. Names/imports are prepared on the phone, sent once, then explicitly received/reviewed/applied on the watch. Cancel applies nothing. Duplicates/conflicts are skipped, not overwritten.

Each phone action retains its caller and export scope. A differently filtered snapshot requires ending an open transfer first. Returning to the scoreboard while creating/receiving must prevent a late request completion from redirecting the UI. No polling is added. Keys/tokens stay in memory/fragments; ciphertext and hashed capabilities remain temporary. End/apply deletes live transfer data; unused expired data is inaccessible and cleaned in later API traffic. No account or permanent server history is introduced.

## Availability, power and diagnostics

An explicit active_match_id, unfinished match and no pause are required for the ongoing service/ambient observer. First point or Start/Resume activates it. Pause, completion, task removal and new-match replacement clear it. Old unfinished scores wait for Resume. Undo to zero during active play retains the session; Undo after completion does not resume automatically. Opening panels releases the scoreboard screen flag while preserving a separately active session. QR awake time is bounded to one minute and releases on backgrounding. No CPU wake lock, sensor sampling or background update polling is added.

Settings contains the version and explicit Check for updates; a verified HTTPS APK installs over the existing app with its original signer/package. The phone installer remains the fallback. Diagnostics → Send to phone uses a separate local-fragment QR and returns to Diagnostics. Diagnostic data stays private until explicitly exported. Whole-watch battery snapshots are on-demand and do not attribute energy consumption to Keep Score.

## Landing page and sharing

Public landing page → current APK/install guide or transfer-privacy explanation. There is no generic transfer launcher or historical APK link list. Optional phone access starts on the watch → QR → isolated encrypted session → explicit phone/watch changes. A bare transfer-page URL shows instructions only. Each user receives separate secrets; keep the complete active QR link private. Expiry stops access to the relay, not access to an already downloaded CSV. Retained APK files remain unchanged for recovery.

Documentation and release maintenance routes are in [README.md](README.md), [DEPLOYMENT.md](DEPLOYMENT.md) and [GITHUB.md](GITHUB.md).

## Serving at a glance

The serving side has a pale-gold outline and dedicated badge, independent of the opponent header. Singles show SERVING; known doubles servers show SERVE plus ME, PARTNER, OPP. 1 or OPP. 2. An unknown individual may retain a known serving-team indicator. Names fit the card width and accessibility retains the full server name. Known service remains visible in ambient view. Unknown service and completed games/matches show no guessed serving cue.

## Keep Score additions

Players → Your name → Save your name (blank means Me) changes your display name independently of saved opponents. Phone editing also requires receive/review/apply. New match → Save as favourite stores a named format/length; Favourite formats reuses it in setup before Create. A current completed result → Quick rematch creates a new identity with the same rules and available players; no score or start time is copied.

Settings → Colour scheme opens an explicit list with both card colours previewed: Classic (blue/red), Neon Lime (lime/pink), Sunlight (white/yellow with black scores). Choosing saves for all sports and keeps this selector open with the current tick. Back to Settings returns explicitly. Ambient stays monochrome. Brightness: watch system → Open watch display settings delegates brightness to Wear OS; no app override or light sensing is added. Vibrations and Court/interval reminders have separate selected lists.

Settings → Backup & restore → Create or restore backup → phone QR → download or select JSON → send → watch Receive changes → Restore this backup? → Replace data & restore. Preview/cancel changes nothing; stale/expired previews cannot apply. The replacement is atomic and unfinished restores are paused. Download previous data copy exports the pre-restore recovery snapshot through a fresh backup link. End an existing transfer before changing its snapshot purpose.

Live sharing & referee → Audience live link → Start live sharing → audience QR opens automatically when ready. Reopen it later with Show audience QR. A separate read-only link shares this match only for up to eight hours during play, then ten minutes for the final result. Stop sharing revokes it if online; offline status explains expiry. Offline scoring continues and queues the latest score. A completed result → Share result card opens the private phone result view with Save/Share image actions.

Add Keep Score from the watch’s tile picker or the watch face’s complication picker. Tapping opens Current match or sport selection without scoring or resuming. These are optional read-only entry points.

The audience page adapts to portrait phones and short landscape screens. Portrait keeps two large aligned cards; landscape places match details and controls alongside them. Full team names wrap, serving has its own badge, and viewers independently choose Classic/Neon Lime/Sunlight. Browser QA covers actual 393×852 and 852×393 CSS viewports with synthetic doubles names, aligned scores, all controls reachable and no horizontal overflow. These are browser layout checks, not physical phone acceptance.

## v0.16.1 navigation and sharing corrections

Settings groups language and display, feedback, data, watch shortcuts and app information. Vibration, reminders, screen policy and colour choices save immediately and stay on their selector with an updated tick. Reselecting the current option does nothing. Back to Settings, hardware Back and swipe use the same route; settings and selector scroll positions are retained. Other one-shot match-setup pickers still return to their draft after a changed choice.

Live readiness automatically opens Audience QR only from the still-visible waiting panel. Failure replaces the waiting controls with Start live sharing; late/background callbacks never interrupt another screen. Returning from QR keeps the ready link available without reopening it repeatedly. QR screen retention stays bounded.

A committed Cancel match → Discard match sends an encrypted anonymous discard notice, replacing all shared scores/names. The viewer shows Match discarded and stops polling. Cancelling the discard confirmation changes nothing. Offline notices retry on network reconnect while the process survives; no score action is required. A stopped/expired/revoked audience link clears the old Playing score and shows Broadcast ended.

## v0.16.2 menu identity

Shared screens use the Keep Score app name, including Players, Settings and their selectors. The sport selector uses the same badge. The App heading groups app settings. Sport-specific pages retain their sport name/icon; Back and swipe routes retain their caller.

## v0.16.3 sport order and audience results

Choose sport lists Badminton, Padel, Pickleball, Squash, Table tennis, Tennis; phone sport selectors and player sport badges use the same order. Existing selected/current sports retain their identities and locks.

The audience dashboard shows previous head-to-head wins/losses plus the last meeting date, overall result and game/set scores when the same saved opponent or complete doubles teams played in this sport/mode. It omits unknown players and the current match. These are previous meetings, including on the final screen. The share and copy actions are accessible icons with titles; unsupported native sharing falls back to copying.

Completion shows the winner and final games/sets, preserves actual partial scores for retirement, and displays the remaining final window. The link expires ten minutes after completion, with repeated updates retaining the deadline. A delayed offline completion uses its completion timestamp; new-match/task dismissal can retry the final snapshot without revoking the completed link. Undo before expiry returns to play using the original live deadline. Stop sharing can still revoke access sooner. Expiry/discard removes current names, scores and previous-meeting details.

## v0.16.4 reset all result filters

Results → Filters → Reset all filters is the first filter action. Filtered Results also offers a direct Reset all filters row, including empty result lists. It clears opponent/player, From, To and Singles/Doubles mode, retains the selected sport, and shows page one at the top. Ordinary Back/details navigation still preserves filters and position. The phone action uses the same label, resets mode/player/dates within the shared snapshot, and refreshes all-mode statistics.

## Linked matches and language

Live sharing & referee appears below the sports on the launcher, near the top of each sport menu and directly after Start, Pause or Resume in Current match. During play, tap the scoreboard’s sport/Menu header. The hub offers Audience live link, Referee on phone, Share with watches and Join with watch code. Opening the hub does not start sharing. Starting without a match takes you through sport and match setup before you confirm. Referee on phone shows a private scoring QR after the current state reaches the server and locks local score actions immediately. Share with watches starts a ten-character read-only code. A watch scorer can hand the same match to a referee while followers stay connected. In referee mode, Invite other watches enables the same code. The host can redisplay the QR and enable or disable new joins.

A receiving watch opens Live sharing & referee → Join with watch code and enters the two groups of five characters. The scoreboard and its accessibility action open Shared match. Set scores, Live sharing & referee and Settings are sibling destinations. A final result appears here for explicit saving. After confirmation, the host chooses Save to my results. A follower also selects their player position. An interrupted session reopens with its last received score. Leaving a followed game preserves local history. Cancelling an unfinished referee-hosted game discards its frozen local draft.

On every watch the language selector is under Settings → Language. From a read-only scoreboard, tap Match menu → Settings. Back goes to the shared match menu and then the scoreboard, including after a language change. All phone pages have a language menu. Changing language preserves the referee queue and unsent roster changes.

## Match journal entry and correction

Scan the Results phone QR. **Add result** opens the sport, mode, rules, date, team and game/set fields. **Edit result** in a saved match opens its existing scores. Leave unused game rows blank. Choose a side under Match finish if someone retired. The recorded format and recorded timestamps are retained. Stage a result to see its effect in the journal and statistics. A staged result can be reopened or discarded before sending. **Send changes → Receive changes → Apply changes** saves the batch to the watch.


## Pairing and removing devices

Use the same ten-character code for every player watch. Each receiving watch opens Live sharing & referee → Join with watch code. A referee scans the dedicated phone QR. In a new v0.20.0 room the host opens Live sharing & referee → Connected devices, matches the short device identifier, selects a device and confirms Remove device. Refresh obtains the latest contact times. The referee page shows its identifier below the match and a player watch shows its identifier in Live sharing & referee. Offline devices can still display cached scores. A removed device cannot fetch updates or upload buffered points when it next connects. Hiding the code closes new watch admissions. Rooms created before v0.20.0 keep the previous sharing controls.
