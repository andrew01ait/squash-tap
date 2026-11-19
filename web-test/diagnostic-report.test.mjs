import assert from 'node:assert/strict';
import test from 'node:test';
import {withWatchState} from '../public/diagnostic-report.mjs';

test('compact watch state expands in the watch language with the latest event intact', () => {
  const trace = 'Keep Score\nRecent watch event';
  const flags = ((1 << 10) | (1 << 14) | 1).toString(36);
  const report = withWatchState(trace, '1.de.' + flags + '.72.9.7.2.1.3.5');
  assert.ok(report.startsWith(trace));
  assert.match(report, /Akku der Uhr: 72% · wird geladen/);
  assert.match(report, /Spielstand: 9–7 · Sätze 2–1/);
  assert.match(report, /Gespeicherte Ergebnisse: 5/);
  assert.match(report, /Partie aktiv: true/);
  assert.match(report, /Benachrichtigungen aktiviert: false/);
  for (const lang of ['en','fr','es']) assert.ok(withWatchState(trace, '1.' + lang + '.0.-1.0.0.0.0.3.0').includes('—'));
});

test('old and malformed state fragments preserve the original report', () => {
  for (const value of [null, '', '2.de.0.42.0.0.0.0.3.0', '1.en.0.120.0.0.0.0.3.0',
    '1.__proto__.0.42.0.0.0.0.3.0', '1.en.bad..42', '1.en.zzz.42.0.0.0.0.3.0']) {
    assert.equal(withWatchState('Watch report', value), 'Watch report');
  }
});
