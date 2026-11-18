# Keep Score components

Keep Score v0.20.1 runs on Wear OS and supports six racket sports. The watch stores match history, scoring rules and player names. Phones and tablets can open a match journal, follow an audience scoreboard or score a match as referee.

## Watch app

- The scoring classes in `android/src` calculate points, service order and match outcomes. Completed matches provide the data for statistics.
- The native watch UI uses contextual menus for match setup, active play, following and results. Each multi-purpose menu exposes its translated section headings to Android accessibility services. Settings contains watch language for every role.
- The score view reserves separate space for names, points and serving. Round and rectangular layouts use the same geometry for drawing each scorecard and detecting taps.

## Web interfaces and tests

The web interfaces live in `public` and the hosted application in `app` and `worker`. The referee view can retain scoring changes while disconnected. Watch users review journal edits before applying them.

The core, native UI and web test suites exercise scoring rules, navigation and interoperability. The native suite renders round, square and rectangular displays at multiple sizes and text scales. See [watch menus](FLOW.md), [sport rules](RULES.md) and [test coverage](TESTING.md).
