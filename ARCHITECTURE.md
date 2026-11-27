# Keep Score components

Keep Score v0.23.9 runs on Wear OS and supports six racket sports. The watch stores match history, scoring rules and player names. Phones and tablets can open a match journal, follow an audience scoreboard or score a match as referee.

The idle Home header and Wear OS Tile use the app name and launcher artwork. MenuLocation carries the section for every watch panel. Home tiles, menu titles and section headings use the destination colours. Watch buttons use a shared neutral style, with a filled primary action, chevrons for submenus and an outlined Back. Actions opening match-ending or deletion choices use a subtle dark fill. Deleting entries and discarding work use red. Sport selectors keep sport colours. The phone journal uses the corresponding accents for its tabs and headings.

## Watch app

`SpokenScore` formats accepted rallies and corrections from the watch’s scoring and serving models. It derives spoken first names from the four original player labels. Host and following watches use the same formatter for the set leader, tie-break points, service handovers and closing scores. A following watch compares successive snapshots and reports a service event only for a single observed rally. `ScoreSpeech` uses an installed offline Android text-to-speech voice in the app language. A newer call replaces a queued score. Pausing the activity, entering ambient mode or turning speech off releases the engine.

- The scoring classes in `android/src` calculate points, service order and match outcomes. Completed matches provide the data for statistics.
- The native watch UI uses contextual menus for match setup, active play, following and results. Each multi-purpose menu exposes its translated section headings to Android accessibility services. The player catalog is accessible from the launcher and each sport menu. Catalog screens and player pickers share a panel for search, recency and sorting above the saved player rows. A current match has its own player or team selector. Shared page components provide headings, action rows, pickers, inputs and contextual Back, including QR pages. Settings contains watch language for every role.
- The host watch exposes confirmed cancellation in the shared match menu and sharing screen. Before discarding the local draft, the room client checks the active room, role and final status under the same lock used to store an incoming referee score.
- The score view reserves separate space for names, points and serving. Round and rectangular layouts use the same geometry for drawing each scorecard and detecting taps.

FinishedMatchReview ties the last user interaction and the closed marker to the saved match ID and finish time. While foregrounded, the watch schedules one deadline. Resume, launcher and widget reads evaluate the same persisted times. A legacy result with no known finish time starts its review clock when first opened. Closing review preserves the scorebook and sharing state. A confirmed, saved shared result can be reviewed while the local watch is idle. Unclaimed remote results keep their explicit save and seat selection.

The transient menu path records the actual caller, stable screen key and title. Tapping the title shows the path and announces it to the screen reader. Results renders the first matching card before its adaptive Filter and Options controls and restores the prior reading position on return from details. Returning to the player directory restores that visit’s search and page. Player recency is derived from match dates and role assignments and uses the same identities as the directory. No per-player timestamps are stored.

## Diagnostic QR

The watch keeps twelve timestamped events with up to 160 characters each. Recording a new event also trims logs from earlier installations. The export reserves space for the app version, time and newest event. When only routine activity events follow an NFC sharing entry, that NFC entry gets priority. Device information is included when space permits. It adds a crash summary and older events as space allows, then checks whether other status details fit. The size check covers the complete URL after gzip and base64url encoding. Its 318-byte limit fits a version 11-L QR with a four-module quiet zone. A separate field in the same URL fragment holds the battery, match score and device flags. The phone expands these into the watch language. Links from earlier watches still open directly. The watch’s Copy details action includes the stored crash stack and system exit descriptions.

## NFC handover

The installed watch app declares both NFC Forum Type 4 NDEF identifiers (mapping v2 and v1) in Android’s non-payment category. Opening the handover page prepares the complete record in memory before requesting foreground routing. Outside an active handover the service rejects every read. Browser handovers carry one HTTPS URI record including its fragment. Watch invitations carry one Keep Score external record containing the existing ten-character watch code. The foreground reader accepts only this invitation format and passes it to the ordinary read-only room join. The HCE service keeps the offered file in memory and enforces its elapsed-time deadline on every read. The capability file advertises the mapping requested by each reader. Local diagnostics record the foreground preference result, the reported default for each identifier, the command count, the last read stage, what ended the offer and its elapsed duration in seconds. The record omits the link, invitation and command bytes. The watch reports contact on application selection and reports a complete read once a single reader session has requested every payload byte. It removes the offer on navigation or backgrounding. The match and its score updates still use their existing network connection.

## Web interfaces and tests

The web interfaces live in `public` and the hosted application in `app` and `worker`. The referee view can retain scoring changes while disconnected. Watch users review journal edits before applying them.

The core, native UI and web test suites exercise scoring rules, navigation and interoperability. The native suite renders round, square and rectangular displays at multiple sizes and text scales. See [watch menus](FLOW.md), [sport rules](RULES.md) and [test coverage](TESTING.md).
