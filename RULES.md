# Sports and scoring — v0.16.1

Choose scoring presets from explicit lists before creating a match. Each match keeps its chosen rules. An unfinished or paused match must be completed or cancelled before switching sports.

Keep Score records point winners. It does not record first/second service attempts, lets, strokes, faults or rally classifications. For a let, leave the score unchanged. For an awarded point, tap its recipient. For traditional pickleball, tap every rally winner, including a receiving-side rally that changes service without scoring a point. Optional serving indicators are derived from recorded points and initial choices; Undo restores them. These presets are scorekeeping formats, not a claim to automate all tournament regulations.

## Supported formats

| Sport | Players | Game/set rules | Match formats |
| --- | --- | --- | --- |
| Squash | Singles | PAR-11, win by two, no cap | Best of 3 or 5 games |
| Badminton | Singles/doubles | 21 points, win by two, cap 30; or 15 points, win by two, cap 21 | Best of 3 games |
| Tennis | Singles/doubles | 0/15/30/40, advantage or No-Ad; six-game sets, win by two; seven-point tie-break at 6–6, win by two | Best of 3 or 5 sets; optional ten-point match tie-break replacing the deciding set |
| Padel | Doubles | Tennis-style sets; Advantage, Golden Point or Star Point; seven-point tie-break at 6–6, win by two | Best of 3 sets; optional ten-point deciding match tie-break |
| Table tennis | Singles/doubles | 11 points, win by two, no cap | Best of 3, 5 or 7 games |
| Pickleball | Singles/doubles | 11, 15 or 21, win by two; Traditional side-out, Rally 2025 (win on serve), Rally 2026 (either side can win) | One game or best of 3/5 |

A match saves its exact preset. Rule updates and new-match preferences never change current or historical matches. Badminton 3×15 takes effect internationally on 4 January 2027; specified ÖBV competitions adopt it on 1 January 2027. Domestic exceptions exist. Both 21/30 and 15/21 remain explicit choices. Padel retains traditional Advantage and Golden Point alongside Star Point introduced on FIP circuits in 2026. Star Point permits two unsuccessful advantage cycles, then one deciding point at the third deuce.

Tennis/padel games advance automatically when won. Between their sets, and between rally-scored games, NEXT remains explicit. A deciding match tie-break is stored and shown separately from ordinary games. An entered 7–6 set does not invent its unknown tie-break points. Retirement retains known partial tennis game/tie-break points as well as completed games. Manually entered tennis/padel summaries do not invent rally counts; statistics count known games and identify unknown point totals.

Pickleball keeps the USAP 2025 provisional rally option (game-winning point must be won on serve) and the 2026 option (receiving side may score the winning point) as explicit frozen presets. Traditional doubles starts each game with the initial team’s second-server call, 0–0–2; subsequent service possessions allow two servers. Rally doubles uses one server per possession. Starting players on both sides are selected before scoring; they cannot be reselected after a recorded rally because that could reinterpret side-out points. Undo all rallies or start a new game to correct that choice. Tournament-specific rally eligibility, mini singles and technical-foul score adjustments are not automated.

## Optional service guidance

- Squash: the rally winner serves next. Before the first point, the initial server may be selected. An entered game's winner provides the next game's serving side. Service boxes are not tracked: a new server chooses a box; retained serves alternate boxes; a let retains the box.
- Badminton: the rally winner serves. For doubles, initial server/receiver choices for each game establish the court rotation. Without sufficient choices, show a known team or no indicator, never guess an individual.
- Tennis/padel: choose the first server, and in doubles the next opposing server. Service rotates each ordinary game; tie-break service is one point, then two points per server. Order continues between sets, with optional correction of the chosen individuals at a new set.
- Table tennis: two points per server, one from 10–10. Doubles follows receiver → server and previous server's partner → receiver; the receiving order changes at five in the deciding game.

- Pickleball: select the initial server and the other team’s starting player. Traditional doubles tracks server one/two and side-outs; rally formats use one server. Court and score calls follow the chosen players and team totals. Non-point rallies remain in the Undo log.

The indicator marks the serving card and, where known, the individual in doubles. Names remain optional. Initial choices can be corrected without changing points. Missing initial choices or manually entered summaries may leave service unknown. Receiving-side choices for No-Ad/Golden/Star points, court changes, tournament expedite rules and referee calls remain with the players.

## Squash audit

The existing PAR-11/no-cap, BO3/BO5, retirement and Undo behavior matches the scoring rules. v0.14 adds Game Ball/Match Ball cues and optional server guidance. A squash match is normally BO5; BO3 is also permitted. Existing saved preferences remain valid.

This is a scorekeeper rather than an officiating application. Optional Settings → Court/interval reminders shows brief change-ends and interval prompts; it never pauses play or starts a countdown. Badminton uses 11/8-point intervals for the selected 21/15-point preset and changes ends there in the deciding game. Table tennis changes ends at five in the deciding game. Pickleball single/deciding games use six/eight/eleven for targets 11/15/21. Tennis/padel changes ends after odd games and each six tie-break points, with set interval prompts. Entered summaries do not invent missed rally prompts.

Warm-up and interval countdowns, injury/blood recovery categories, awarded/conceded conduct games, conduct-match outcomes and walkovers are not automated. An awarded/conceded game retains the actual score under the rules; entering a fabricated 11–x result would be incorrect. Retirement is the supported explicit early-end outcome. Match elapsed time includes pauses and breaks.

## Primary references

- [World Squash singles rules, September 2025](https://worldsquashofficiating.com/rules-of-squash/)
- [ITF Rules of Tennis 2026](https://www.world.tennis/media/7221/2026-rules-of-tennis-english.pdf), Rules 5–7, 14 and Appendix VI
- [BWF Laws of Badminton, effective 4 January 2027](https://ghanabadminton.org/wp-content/uploads/Section-4.1-Laws-of-Badminton-04-January-2027-V6.0.pdf), official BWF text hosted by the Ghana federation
- [ÖBV current rules](https://www.badminton.at/spielregeln.php) and [Austrian 2027 transition](https://www.badminton.at/news/3_15_ab_2027)
- [FIP Rules of Padel 2026](https://www.padelfip.com/wp-content/uploads/2025/12/FIP_Rules-of-Padel-1.pdf), Rule 1 and service rules
- [ITTF Statutes 2026](https://db.ittf.com/sites/default/files/public/2026-02/2026_Statutes_v1_consolidated_clean.pdf), Chapter 2, Laws 2.11–2.13

- [USA Pickleball official rulebook and 2026 edition](https://usapickleball.org/rules/)
- [USAP 2025 rules](https://usapickleball.org/docs/2025-USA-Pickleball-Rulebook.pdf), provisional rally scoring and service rotation
- [USAP 2026 change document](https://usapickleball.org/docs/rules/USAP-Rulebook-Change-Document.pdf), rally game-winning point

A discarded shared match saves no result and replaces the audience display with an anonymous discard notice; it does not alter the scoring rules.
