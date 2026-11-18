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
    for (const value of period.score) {
      const cell = document.createElement('td');
      cell.textContent = String(value);
      row.append(cell);
    }
    return row;
  });
  table.tBodies[0].replaceChildren(...rows);
}
