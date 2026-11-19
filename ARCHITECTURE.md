# Keep Score components

Keep Score v0.21.1 runs on Wear OS and supports six racket sports. The watch stores match history, scoring rules and player names. Phones and tablets can open a match journal, follow an audience scoreboard or score a match as referee.

## Watch app

- The scoring classes in `android/src` calculate points, service order and match outcomes. Completed matches provide the data for statistics.
- The native watch UI uses contextual menus for match setup, active play, following and results. Each multi-purpose menu exposes its translated section headings to Android accessibility services. The player catalog is accessible from the launcher and each sport menu. A current match has its own player or team selector. Shared page components provide headings, action rows, pickers, inputs and contextual Back, including QR pages. Settings contains watch language for every role.
- The score view reserves separate space for names, points and serving. Round and rectangular layouts use the same geometry for drawing each scorecard and detecting taps.

## NFC handover

The installed watch app declares the NFC Forum Type 4 NDEF identifier in Android’s non-payment category. Opening the handover page prepares the complete record in memory before requesting foreground routing. Outside an active handover the service rejects every read. Browser handovers carry one HTTPS URI record including its fragment. Watch invitations carry one Keep Score external record containing the existing ten-character watch code. The foreground reader accepts only this invitation format and passes it to the ordinary read-only room join. The HCE service keeps the offered file in memory and enforces its elapsed-time deadline on every read. The watch reports contact on application selection and reports a complete read once a single reader session has requested every payload byte. It removes the offer on navigation or backgrounding. The match and its score updates still use their existing network connection.

## Web interfaces and tests

The web interfaces live in `public` and the hosted application in `app` and `worker`. The referee view can retain scoring changes while disconnected. Watch users review journal edits before applying them.

The core, native UI and web test suites exercise scoring rules, navigation and interoperability. The native suite renders round, square and rectangular displays at multiple sizes and text scales. See [watch menus](FLOW.md), [sport rules](RULES.md) and [test coverage](TESTING.md).
