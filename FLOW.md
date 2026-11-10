# Navigation and match flows — v0.14.0

## Sport entry and scope

The launcher opens Choose sport: Squash, Tennis, Badminton, Padel, Table tennis, plus shared Players and Settings. Each sport remembers its new-match rules and length. The ongoing shortcut resumes the scoreboard. Change sport changes browsing scope without replacing the current match. A new match from another sport still confirms replacement of an unfinished score/session. There is one current match, not one running match per sport.

Results and Statistics use the chosen sport. Eligible sports offer All modes / Singles / Doubles. Result player filters include all team roles; date/player filters do not narrow Statistics. Delete all results deletes the selected sport only and preserves other sports, current state, daily counters and deletion markers. Phone Results/Statistics have their own shared sport/mode selectors; the Players catalog is global.

New match stages sport-specific rules, length, optional opponent/partner/opponent-2 and optional first-server choices. Padel stays doubles; squash stays singles. Duplicate people in different slots are rejected. Tennis/padel games advance automatically; NEXT remains explicit between sets. Rules are locked to the match; changing new-match defaults does not reinterpret old results. [RULES.md](RULES.md) lists formats and service choices.

Players shows small vector sport badges only for associated saved results as partner or opponent. No-result players have no badges. Delete/Undo, import and role reassignment are reflected on the next render. Names remain optional. Removing a player archives selection, and renaming follows their stable ID across sports.

## Root and priorities

The scoreboard stays focused on Me/Opponent scoring, Undo and Next/New. Its header opens Menu within the selected sport:

| Destination | Purpose | Style |
| --- | --- | --- |
| Current match | Live session, set entry and current scores | Filled blue |
| Results | Saved history, filters, past matches, CSV and phone viewing | Blue navigation row |
| Statistics | All-result match/set/point balances | Blue navigation row |
| Players | Saved name catalog | Purple person control |
| Change sport | Return to the five-sport selector | Blue navigation row |
| Scoring rules | Sport presets, service guidance and limits | Blue navigation row |
| Settings | Version, updates, screen/notification options and diagnostics | Grey |

Menu returns to the scoreboard. Every subpage has one named outlined Back/Cancel action. Hardware Back and native right-swipe dismissal invoke that same return. During active or resumable play, the header has a Scoreboard shortcut; it changes no session state. Dialog replacement, backgrounding and ambient transitions do not act as a user Back.

## Current match

| State | First action | Other actions |
| --- | --- | --- |
| Fresh | Start match | Enter set score, Match details, Match options |
| Playing | Pause match | Enter set score, Match details, Match options |
| Paused / waiting to resume | Resume match | Enter set score, Match details, Match options |
| Completed | View result, when saved | Match details, New match |

The summary shows opponent, sets and BO3/BO5. Match details lists actual set scores and known start timing. Match options holds Opponent, Match format, New match and End match early. These options never contain Settings. Completed matches hide inapplicable live/set/options controls. If result storage is full, the completed scoreboard stays available through Match details until a result can be saved.

Match format changes an unfinished current match in place, preserving UUID, points, sets, timing and Undo. BO5 → BO3 is rejected after either player already has two set wins. Changes return to Match options.

## New-match setup

NEW, Match options → New match or a current result → New match opens a draft with No name and the last-used format. Opponent selection is optional and purple. BO3/BO5 selection changes only the draft. Create match commits a fresh UUID; if an unfinished score would be replaced, Replace & create match must be confirmed. Back to setup retains the draft; Cancel returns to its original caller. Old results remain. Preparing a match does not activate an ongoing session.

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

Current match → Match options → End match early → I quit / Opponent quit → confirmation → save retirement result with actual scores → release the active session. Cancellation preserves the match. Undo retirement reopens all rallies and waits for Resume. Deletion must not revive the result.

## Names

Players centrally adds/renames/removes catalog entries. Remove from opponent list archives future selection while past results retain identity/filterability. Match options and result details open optional saved-name selection. Picker cancellation/save returns to its actual caller, including the current result page. NEW stages selection for the new UUID only. Phone catalog additions/restorations never automatically assign a match opponent; reassignment requires the existing separate explicit opt-in and review.

## Phone links, CSV and privacy

View results on phone, Edit names on phone and Import CSV create/reuse the same encrypted ten-minute QR transfer. The QR's local view fragment selects Results, Opponents or Import after decryption; old/unknown values default to Results. It changes no authorization, snapshot data or encryption. Names/imports are prepared on the phone, sent once, then explicitly received/reviewed/applied on the watch. Cancel applies nothing. Duplicates/conflicts are skipped, not overwritten.

Each phone action retains its caller and export scope. A differently filtered snapshot requires ending an open transfer first. Returning to the scoreboard while creating/receiving must prevent a late request completion from redirecting the UI. No polling is added. Keys/tokens stay in memory/fragments; ciphertext and hashed capabilities remain temporary. End/apply deletes live transfer data; unused expired data is inaccessible and cleaned in later API traffic. No account or permanent server history is introduced.

## Availability, power and diagnostics

An explicit active_match_id, unfinished match and no pause are required for the ongoing service/ambient observer. First point or Start/Resume activates it. Pause, completion, task removal and new-match replacement clear it. Old unfinished scores wait for Resume. Undo to zero during active play retains the session; Undo after completion does not resume automatically. Opening panels releases the scoreboard screen flag while preserving a separately active session. QR awake time is bounded to one minute and releases on backgrounding. No CPU wake lock, sensor sampling or background update polling is added.

Settings contains the version and explicit Check for updates; a verified HTTPS APK installs over the existing app with its original signer/package. The phone installer remains the fallback. Diagnostics → Send to phone uses a separate local-fragment QR and returns to Diagnostics. Diagnostic data stays private until explicitly exported. Whole-watch battery snapshots are on-demand and do not attribute energy consumption to Squash Tap.

## Landing page and sharing

Public landing page → current APK/install guide or transfer-privacy explanation. There is no generic transfer launcher or historical APK link list. Optional phone access starts on the watch → QR → isolated encrypted session → explicit phone/watch changes. A bare transfer-page URL shows instructions only. Each user receives separate secrets; keep the complete active QR link private. Expiry stops access to the relay, not access to an already downloaded CSV. Retained APK files remain unchanged for recovery.

Documentation and release maintenance routes are in [README.md](README.md), [DEPLOYMENT.md](DEPLOYMENT.md) and [GITHUB.md](GITHUB.md).
