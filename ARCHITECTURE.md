# Keep Score components

Keep Score v0.21.3 runs on Wear OS and supports six racket sports. The watch stores match history, scoring rules and player names. Phones and tablets can open a match journal, follow an audience scoreboard or score a match as referee.

## Watch app

- The scoring classes in `android/src` calculate points, service order and match outcomes. Completed matches provide the data for statistics.
- The native watch UI uses contextual menus for match setup, active play, following and results. Each multi-purpose menu exposes its translated section headings to Android accessibility services. The player catalog is accessible from the launcher and each sport menu. A current match has its own player or team selector. Shared page components provide headings, action rows, pickers, inputs and contextual Back, including QR pages. Settings contains watch language for every role.
- The score view reserves separate space for names, points and serving. Round and rectangular layouts use the same geometry for drawing each scorecard and detecting taps.

## Diagnostic QR

The watch keeps twelve timestamped events with up to 160 characters each. Recording a new event also trims logs from earlier installations. The export includes the app version, time, device information and the newest event. It adds a crash summary and older events as space allows, then checks whether other status details fit. The size check covers the complete URL after gzip and base64url encoding. Its 318-byte limit fits a version 11-L QR with a four-module quiet zone. A separate field in the same URL fragment holds the battery, match score and device flags. The phone expands these into the watch language. Links from earlier watches still open directly. The watch’s Copy details action includes the stored crash stack and system exit descriptions.

## NFC handover

The installed watch app declares both NFC Forum Type 4 NDEF identifiers (mapping v2 and v1) in Android’s non-payment category. Opening the handover page prepares the complete record in memory before requesting foreground routing. Outside an active handover the service rejects every read. Browser handovers carry one HTTPS URI record including its fragment. Watch invitations carry one Keep Score external record containing the existing ten-character watch code. The foreground reader accepts only this invitation format and passes it to the ordinary read-only room join. The HCE service keeps the offered file in memory and enforces its elapsed-time deadline on every read. The capability file advertises the mapping requested by each reader. Local diagnostics record the foreground preference result, the reported default for each identifier, the command count and the last read stage. The record omits the link, invitation and command bytes. The watch reports contact on application selection and reports a complete read once a single reader session has requested every payload byte. It removes the offer on navigation or backgrounding. The match and its score updates still use their existing network connection.

## Web interfaces and tests

The web interfaces live in `public` and the hosted application in `app` and `worker`. The referee view can retain scoring changes while disconnected. Watch users review journal edits before applying them.

The core, native UI and web test suites exercise scoring rules, navigation and interoperability. The native suite renders round, square and rectangular displays at multiple sizes and text scales. See [watch menus](FLOW.md), [sport rules](RULES.md) and [test coverage](TESTING.md).
