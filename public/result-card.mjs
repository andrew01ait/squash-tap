import {rowRules,scorePairs,finalScore,details} from './sports.mjs?v=0.16.3';
import {rowWinner} from './interchange.mjs?v=0.16.0';
import {datedFile} from './backup.mjs?v=0.16.0';
import {matchDuration} from './transfer-state.mjs?v=8';
import {tx,sportText,modeText,momentText,durationText} from './transfer-copy.mjs?v=11';

const width = 1080, margin = 64, centers = [272, 808];
const fontFamily = 'Inter, Roboto, system-ui, sans-serif';
const ink = '#182b37', mutedInk = '#50626b', paper = '#f5f3eb', white = '#f5f8fa';
const teamColors = ['#82bcff', '#ff9cad'];

function textAt(ctx, value, x, y, size, weight, color, align = 'left') {
  ctx.font = weight + ' ' + size + 'px ' + fontFamily;
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.fillText(String(value), x, y);
}

function roundedBox(ctx, x, y, w, h, radius, color) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, radius);
  ctx.fillStyle = color;
  ctx.fill();
}

function brand(ctx) {
  // The two score tiles and their white points are the Keep Score launcher mark.
  ctx.save();
  ctx.translate(margin, 42);
  ctx.fillStyle = '#080d17';
  ctx.beginPath(); ctx.arc(24, 24, 24, 0, Math.PI * 2); ctx.fill();
  ctx.fillStyle = '#2489f5'; ctx.fillRect(8, 12, 14, 24);
  ctx.fillStyle = '#f34b65'; ctx.fillRect(26, 12, 14, 24);
  ctx.fillStyle = '#fff'; ctx.fillRect(12, 21, 6, 6); ctx.fillRect(30, 21, 6, 6);
  ctx.restore();
  textAt(ctx, 'Keep Score', margin + 62, 78, 34, 700, white);
}

// Prefer word boundaries, including in team names. Split exceptionally long words
// at grapheme boundaries so the full name stays legible at the same font size.
function nameLines(ctx, name, maxWidth) {
  const lines = [];
  let line = '';
  for (const word of name.trim().split(/\s+/u)) {
    const next = line ? line + ' ' + word : word;
    if (ctx.measureText(next).width <= maxWidth) { line = next; continue; }
    if (line) lines.push(line);
    line = '';
    if (ctx.measureText(word).width <= maxWidth) { line = word; continue; }
    const letters = typeof Intl.Segmenter === 'function'
      ? Array.from(new Intl.Segmenter(undefined, {granularity:'grapheme'}).segment(word), part => part.segment)
      : Array.from(word);
    for (const letter of letters) {
      if (line && ctx.measureText(line + letter).width > maxWidth) { lines.push(line); line = ''; }
      line += letter;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function check(ctx, x, y, size, color) {
  ctx.save();
  ctx.strokeStyle = color; ctx.lineWidth = size * .15;
  ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  ctx.beginPath();
  ctx.moveTo(x, y); ctx.lineTo(x + size * .34, y + size * .32); ctx.lineTo(x + size, y - size * .42);
  ctx.stroke(); ctx.restore();
}

// The winning number and the check form one centered group.
function checkedValue(ctx, value, x, centerY, size, dark) {
  const color = dark ? '#baf3d1' : '#125538';
  const background = dark ? '#1b4235' : '#d7e9d9';
  ctx.font = '700 ' + size + 'px ' + fontFamily;
  const numberWidth = ctx.measureText(value).width, markSize = size * .57;
  const contentWidth = numberWidth + markSize + 12, height = Math.round(size * 1.46);
  const boxWidth = Math.max(108, contentWidth + 40);
  roundedBox(ctx, x - boxWidth / 2, centerY - height / 2, boxWidth, height, height / 2, background);
  const left = x - contentWidth / 2;
  check(ctx, left, centerY + 1, markSize, color);
  textAt(ctx, value, left + markSize + 12, centerY + size * .34, size, 700, color);
}

export async function resultCardBlob(row, opponent, myName = tx('Me')) {
  const rules = rowRules(row), winner = rowWinner(row);
  const sides = rules.doubles
    ? [[myName, row[33] || tx('Partner')], [opponent, row[35] || tx('Opponent 2')]]
    : [[myName], [opponent]];
  const canvas = document.createElement('canvas');
  canvas.width = width;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw Error('image');
  const nameSize = rules.doubles ? 35 : 42, lineHeight = nameSize + 7;
  ctx.font = '700 ' + nameSize + 'px ' + fontFamily;
  const names = sides.map(team => team.flatMap((name, i) => nameLines(ctx, (i ? '+ ' : '') + name, 426)));
  const maxLines = Math.max(...names.map(lines => lines.length));
  const scoreBaseline = 290 + maxLines * lineHeight + 125;
  const headerHeight = scoreBaseline + 96;
  const gameDetails = details(row);
  const games = scorePairs(row).filter(Boolean).map(([a,b], i) => {
    const tie = rules.finalTie && i === Number(row[6]) - 1;
    const complete = finalScore(rules, tie, a, b);
    const recorded = gameDetails[i];
    let note = '';
    if (!complete) note = tx('Unfinished');
    else if (recorded?.[2] >= 0) note = tx('Tie-break') + ' ' + recorded[2] + '–' + recorded[3];
    return {scores:[a,b], winner:complete ? (a > b ? 0 : 1) : -1, note,
      label:tie ? tx('Match tie-break') : tx(rules.nested ? 'Set {0}' : 'Game {0}', i + 1)};
  });
  const rowHeight = game => game.note ? 98 : 82;
  const duration = matchDuration(row);
  const outlierSpace = duration?.outlier ? 31 : 0;
  canvas.height = headerHeight + 115 + outlierSpace + games.reduce((height, game) => height + rowHeight(game), 0) + 40;

  ctx.fillStyle = paper; ctx.fillRect(0, 0, width, canvas.height);
  const gradient = ctx.createLinearGradient(0, 0, width, headerHeight);
  gradient.addColorStop(0, '#172b43'); gradient.addColorStop(1, '#0c1725');
  ctx.fillStyle = gradient; ctx.fillRect(0, 0, width, headerHeight);
  ctx.fillStyle = '#82bcff'; ctx.fillRect(0, 0, width / 2, 7);
  ctx.fillStyle = '#ff9cad'; ctx.fillRect(width / 2, 0, width / 2, 7);
  brand(ctx);
  textAt(ctx, tx(row[23] ? 'Won by retirement' : 'Final result'), width - margin, 77, 27, 600, '#c4d7e7', 'right');
  ctx.fillStyle = '#395064'; ctx.fillRect(margin, 111, width - margin * 2, 2);

  textAt(ctx, sportText(rules.sport), margin, 191, 68, 800, white);
  const metadata = [modeText(rules.doubles ? 'doubles' : 'singles'), tx('Best of {0}', row[6])];
  textAt(ctx, metadata.join('  ·  '), margin, 237, 31, 500, '#bdcedc');
  if (row[2] || row[7]) textAt(ctx, momentText(row, 7, false), width - margin, 237, 29, 500, '#bdcedc', 'right');

  for (const [side, lines] of names.entries()) {
    const x = centers[side];
    roundedBox(ctx, x - 30, 279, 60, 6, 3, teamColors[side]);
    lines.forEach((line, index) => textAt(ctx, line, x, 290 + (index + 1) * lineHeight, nameSize, 700, white, 'center'));
    textAt(ctx, row[11 + side], x, scoreBaseline, 142, 800, white, 'center');
    if (side === winner) checkedValue(ctx, tx('Won'), x, scoreBaseline + 44, 27, true);
  }
  textAt(ctx, '–', width / 2, scoreBaseline - 22, 62, 400, '#8396a7', 'center');

  textAt(ctx, tx(rules.nested ? 'Set scores' : 'Game scores'), margin, headerHeight + 72, 40, 700, ink);
  if (duration) {
    const elapsed = durationText(Math.floor(duration.milliseconds / 1000));
    textAt(ctx, tx('Duration') + ' · ' + elapsed, width - margin, headerHeight + 70, 29, 500, mutedInk, 'right');
    if (duration.outlier) textAt(ctx, tx('Time outlier'), width - margin, headerHeight + 103, 25, 600, '#754b15', 'right');
  }
  let top = headerHeight + 111 + outlierSpace;
  for (const game of games) {
    ctx.fillStyle = '#c9d1ca'; ctx.fillRect(margin, top, width - margin * 2, 2);
    const centerY = top + rowHeight(game) / 2;
    for (const [side, value] of game.scores.entries()) {
      if (side === game.winner) checkedValue(ctx, String(value), centers[side], centerY, 39, false);
      else textAt(ctx, value, centers[side], centerY + 13, 39, 500, ink, 'center');
    }
    textAt(ctx, game.label, width / 2, centerY + (game.note ? -3 : 10), 30, 600, mutedInk, 'center');
    if (game.note) textAt(ctx, game.note, width / 2, centerY + 30, 25, 500, mutedInk, 'center');
    top += rowHeight(game);
  }
  return new Promise((resolve,reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(Error('image')), 'image/png'));
}

function saveBlob(blob,file) {
  const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=file;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
export async function downloadResultCard(row,opponent,myName=tx('Me')){
  const blob=await resultCardBlob(row,opponent,myName);saveBlob(blob,datedFile(rowRules(row).sport+'_Result_'+row[2]+'.png'));
}
export async function shareResultCard(row,opponent,myName=tx('Me')){
  const blob=await resultCardBlob(row,opponent,myName),filename=datedFile(rowRules(row).sport+'_Result_'+row[2]+'.png');
  if(typeof File==='function'&&navigator.canShare) {
    const file=new File([blob],filename,{type:'image/png'});
    if(navigator.canShare({files:[file]})){await navigator.share({files:[file],title:'Keep Score · '+sportText(rowRules(row).sport)});return;}
  }
  saveBlob(blob,filename);
}
