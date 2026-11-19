import {t} from './locale.mjs';

export function winMark() {
  const mark = document.createElement('span');
  mark.className = 'score-win-mark';
  mark.setAttribute('aria-hidden', 'true');
  mark.textContent = '✓';
  return mark;
}

export function scoreValue(value, won) {
  if (!won) return document.createTextNode(String(value));
  const score = document.createElement('strong');
  score.className = 'won-score';
  const description = document.createElement('span');
  description.className = 'score-win-description';
  description.textContent = ' ' + t('Won', 'Gewonnen', 'Victoire', 'Victoria');
  score.append(winMark(), String(value), description);
  return score;
}

/** Present both teams' actual scores for each played game or set. */
export function renderScorecard(table, periodHeading, names, periods) {
  const headers = table.tHead.rows[0].cells;
  for (const [index, text] of [periodHeading, ...names].entries()) headers[index].textContent = text;
  const rows = periods.map(period => {
    const row = document.createElement('tr');
    const title = document.createElement('th');
    title.scope = 'row';
    title.textContent = period.label;
    if (period.current) row.setAttribute('aria-current', 'true');
    if (period.note) {
      const note = document.createElement('span');
      note.className = 'period-note';
      note.textContent = period.note;
      title.append(note);
    }
    row.append(title);
    for (const [side, value] of period.score.entries()) {
      const cell = document.createElement('td');
      cell.append(scoreValue(value, side === period.winner));
      row.append(cell);
    }
    return row;
  });
  table.tBodies[0].replaceChildren(...rows);
}
