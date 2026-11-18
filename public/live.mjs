import {open} from './interchange.mjs?v=0.16.1';
import {sportName, sportMarkup} from './sports.mjs?v=0.16.3';
import {validateLive} from './live-data.mjs?v=locales1';
import {language, mountLanguage} from './locale.mjs';
import {bindPageCopy} from './page-copy.mjs';
import {label, liveCopy, scoreRules, audienceNames} from './live-copy.mjs?v=0.18.1';
import {renderScorecard} from './match-sheet.mjs?v=0.18.1';

const $ = id => document.getElementById(id);
const staticCopy = bindPageCopy(document, liveCopy);
const params = new URLSearchParams(location.hash.slice(1));
const link = {id: params.get('id'), token: params.get('p'), key: params.get('k')};
const validLink = /^[a-f0-9]{32}$/.test(link.id) && /^[-\w]{43}$/.test(link.token) && /^[-\w]{43}$/.test(link.key);
const historyFields = ['history-name-0','history-name-1','history-balance','history-scope','last-date','last-score','last-winner','last-games'];
let timer, expiryTimer, controller, fetching = false, closed, last, updated = 0, expiresAt = 0;
let statusKey = 'Open an audience link shared from a watch.';
let statusError = false;

function status(key, error = false) {
  statusKey = key;
  statusError = error;
  const message = label(key);
  if ($('connection').textContent !== message) $('connection').textContent = message;
  $('connection').classList.toggle('error', error);
}

function showEnded(title, detail) {
  closed = {title, detail};
  clearTimeout(timer);
  clearTimeout(expiryTimer);
  last = null;
  $('scoreboard').hidden = true;
  $('ended').hidden = false;
  $('ended-title').textContent = label(title);
  $('ended-detail').textContent = label(detail);
  for (const id of ['sport','side-label','name-0','name-1','score-0','score-1','sets-0','sets-1','games-0','games-1','sets-heading','games-heading','period-side-0','period-side-1','period-heading','score-unit','score-call','rules','freshness','set-scores','winner-name','final-detail','expires',...historyFields]) {
    $(id).replaceChildren();
  }
  $('last-date').removeAttribute('datetime');
  $('final-result').hidden = true;
  $('head-to-head').hidden = true;
  $('phase').textContent = label('Ended');
  for (let i = 0; i < 2; i++) {
    $('side-' + i).classList.remove('serving', 'winner');
    $('serve-' + i).hidden = true;
    $('serve-' + i).textContent = '';
  }
  status(title);
}

function minutesLeft() {
  return Math.max(1, Math.ceil((expiresAt - Date.now()) / 60000));
}

function clock(time, seconds = false) {
  const options = {hour: '2-digit', minute: '2-digit'};
  if (seconds) options.second = '2-digit';
  return new Date(time).toLocaleTimeString(language(), options);
}

function renderHistory(d, names) {
  const h = d.headToHead;
  $('head-to-head').hidden = !h;
  if (!h) {
    for (const id of historyFields) $(id).replaceChildren();
    $('last-date').removeAttribute('datetime');
    return;
  }
  $('history-name-0').textContent = names[0];
  $('history-name-1').textContent = names[1];
  $('history-balance').textContent = h.won + '–' + h.lost;
  const count = h.won + h.lost;
  $('history-scope').textContent = [
    count === 1 ? label('One previous meeting') : label('{0} previous meetings', count),
    label(d.mode === 'doubles' ? 'same teams' : 'same opponent'),
    label('before this match')
  ].join(' · ');
  $('last-date').dateTime = h.last.date;
  $('last-date').textContent = new Date(h.last.date + 'T12:00:00').toLocaleDateString(language(), {day:'numeric',month:'short',year:'numeric'});
  $('last-score').textContent = h.last.sets.join('–');
  $('last-winner').textContent = label(h.last.retiredPlayer >= 0 ? '{0} won by retirement' : '{0} won', names[h.last.winner]);
  $('last-games').replaceChildren();
  for (const game of h.last.scores) {
    const item = document.createElement('span');
    item.textContent = (game.matchTie ? label('Match tie-break') + ' ' : '') + game.a + '–' + game.b + (game.complete ? '' : label(' · unfinished'));
    $('last-games').append(item);
  }
}

function render(d) {
  const {teams:names, serving:serveName} = audienceNames(d);
  const nested = d.sport === 'tennis' || d.sport === 'padel';
  const final = d.phase === 'finished';
  const lastPeriod = d.setScores[d.setScores.length - 1];
  const showGames = !final && nested && !lastPeriod.matchTie;
  const phase = {ready:'Ready to play',playing:'Playing',paused:'Paused',interval:'Between games',finished:'Finished'};
  const mark = document.createElement('span');
  mark.innerHTML = sportMarkup(d.sport);
  $('scoreboard').hidden = false;
  $('scoreboard').dataset.phase = d.phase;
  $('scoreboard').dataset.columns = final ? 'final' : showGames ? 'nested' : 'rallies';
  $('side-label').textContent = label(d.mode === 'doubles' ? 'Teams' : 'Players');
  $('sets-heading').textContent = label(nested ? 'Sets' : 'Games won');
  $('sets-heading').hidden = final;
  $('games-heading').textContent = label('Games');
  $('games-heading').hidden = !showGames;
  $('score-unit').textContent = label(final ? (nested ? 'Sets' : 'Games won') : 'Points');
  $('sport').replaceChildren(mark, ' ' + label(sportName(d.sport)) + ' · ' + label(d.mode === 'doubles' ? 'Doubles' : 'Singles'));
  $('phase').textContent = final ? label('Final · {0} min left', minutesLeft()) : label(phase[d.phase]);
  $('final-result').hidden = !final;
  $('winner-name').textContent = final ? label('{0} wins', names[d.winner]) : '';
  $('final-detail').textContent = !final ? '' : d.retiredPlayer >= 0
    ? label('By retirement · actual scores shown below')
    : label('Best of {0} · {1}', d.bestOf || Math.max(...d.sets)*2-1, label(nested ? 'sets' : 'games'));

  for (let i = 0; i < 2; i++) {
    $('name-'+i).textContent = names[i];
    const points = String(final ? d.sets[i] : d.points[i]);
    $('score-'+i).textContent = points;
    $('score-'+i).dataset.length = String(points.length);
    $('sets-'+i).textContent = d.sets[i];
    $('sets-'+i).hidden = final;
    const games = i === 0 ? lastPeriod.a : lastPeriod.b;
    $('games-'+i).textContent = games;
    $('games-'+i).dataset.wide = String(games >= 1000);
    $('games-'+i).hidden = !showGames;
    const serves = !final && d.server >= 0 && (d.server >= 4 ? d.server-4 : d.server%2) === i;
    $('side-'+i).classList.toggle('serving', serves);
    $('side-'+i).classList.toggle('winner', final && d.winner === i);
    $('serve-'+i).hidden = !serves;
    $('serve-'+i).textContent = serves ? label('Serving · {0}', serveName) : label('Serving');
  }

  $('score-call').textContent = !final && d.scoreCall ? label('Serve call: {0}', d.scoreCall) : '';
  $('rules').textContent = scoreRules(d);
  const unit = label(nested ? 'Set' : 'Game');
  renderScorecard($('scorecard'), unit, names, d.setScores.map((game, index) => {
    const current = index === d.setScores.length - 1 && !final && d.phase !== 'interval' && game.complete !== true;
    return {
      label: game.matchTie ? label('Match tie-break') : unit + ' ' + (index + 1),
      score: [game.a, game.b], current,
      note: current ? label('Current') : final && game.complete === false ? label('Unfinished') : ''
    };
  }));
  $('freshness').textContent = label('Last score update {0}. Updates arrive after a rally or match action.', clock(updated, true));
  $('expires').textContent = final ? label('Final result available until {0} · {1} min remaining',clock(expiresAt),minutesLeft()) : '';
  renderHistory(d, names);
}

function renderLanguage() {
  staticCopy();
  if (closed) showEnded(closed.title, closed.detail);
  else {
    if (last) render(last);
    status(statusKey, statusError);
  }
}
mountLanguage($('audience-language'), renderLanguage);
renderLanguage();

function expire() {
  showEnded('Broadcast ended', 'This audience link has expired. Final results remain available for 10 minutes after completion.');
}

async function poll() {
  clearTimeout(timer);
  if (!validLink || fetching || closed || document.hidden) return;
  fetching = true;
  controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const r = await fetch('/api/live/' + link.id, {
      cache:'no-store', credentials:'omit', redirect:'error',
      headers:{Authorization:'Bearer ' + link.token}, signal:controller.signal
    });
    if ([401,403,404].includes(r.status)) {
      showEnded('Broadcast ended', 'This match is no longer available on this audience link. Sharing has stopped or the link has expired.');
      return;
    }
    if (!r.ok) throw Error('Live score service unavailable');
    const data = await r.json();
    if (!Number.isFinite(data.updatedAt) || !Number.isFinite(data.expiresAt)) throw Error('Invalid live response');
    if (Date.now() >= data.expiresAt) { expire(); return; }
    const score = validateLive(JSON.parse(await open(link.id, 'out', link.key, data.cipher)));
    if (closed) return;
    if (score.phase === 'discarded') {
      showEnded('Match discarded', 'The scoring watch discarded this match. No result was saved.');
      return;
    }
    last = score;
    updated = data.updatedAt;
    expiresAt = data.expiresAt;
    clearTimeout(expiryTimer);
    expiryTimer = setTimeout(expire, Math.max(0, expiresAt - Date.now()));
    render(score);
    status(score.phase === 'finished' ? 'Match finished · final result' : 'Connected · following this match');
  } catch {
    if (!document.hidden && !closed) status(last ? 'Connection interrupted · showing the last received score' : 'Could not connect · retrying shortly', true);
  } finally {
    clearTimeout(timeout);
    fetching = false;
    controller = null;
    if (!closed && !document.hidden) timer = setTimeout(poll, last?.phase === 'finished' ? 10000 : 3000);
  }
}

document.addEventListener('visibilitychange', () => {
  clearTimeout(timer);
  if (document.hidden) controller?.abort();
  else if (!closed && expiresAt && Date.now() >= expiresAt) expire();
  else poll();
});
window.addEventListener('online', poll);
$('palette').addEventListener('change', () => { document.body.dataset.palette = $('palette').value; });
$('copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(location.href); status('Audience link copied'); }
  catch { status('Copy this page’s address to share the audience link.'); }
});
$('share').addEventListener('click', async () => {
  try {
    if (navigator.share) await navigator.share({title:label('Keep Score · live match'),url:location.href});
    else $('copy').click();
  } catch (e) { if (e.name !== 'AbortError') status('Use Copy audience link to share.'); }
});
$('fullscreen').addEventListener('click', async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch { status('Fullscreen is unavailable in this browser.'); }
});
if (validLink) { status('Connecting to the live match…'); poll(); }
else if (link.id || link.token || link.key) status('Invalid audience link. Scan a fresh QR code from the scoring watch.', true);
