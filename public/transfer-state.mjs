import {todayLocal} from './result-entry.mjs?v=2';
import {participants, rowMode, rowSport, scorePairs, sportRows} from './sports.mjs?v=0.16.3';
import {name, statistics, localDate, rowWinner} from './interchange.mjs?v=0.16.0';

export function initialView(value){return ['results','statistics','opponents','import','backup'].includes(value)?value:'results';}

export const EMPTY_MATCH_NOTES=Object.freeze({text:'',event:''});

function noteField(value,limit,multiline=false){
  if(typeof value!=='string') throw Error('Use plain text for match notes.');
  const normalized=multiline?value.replace(/\r\n?/g,'\n'):value;
  const controls=multiline?/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f]/u:/[\u0000-\u001f\u007f-\u009f]/u;
  if(controls.test(normalized)||/[\ud800-\udfff]/u.test(normalized)) throw Error('Use plain text for match notes.');
  const trimmed=normalized.replace(/^[ \t\n]+|[ \t\n]+$/g,'');
  if(trimmed.length>limit) throw Error('A match note field is too long.');
  return trimmed;
}

export function normalizeMatchNotes(value){
  if(!value||typeof value!=='object'||Array.isArray(value)) throw Error('Use plain text for match notes.');
  return {text:noteField(value.text,600,true),event:noteField(value.event,80)};
}

export const sameMatchNotes=(a,b)=>a.text===b.text&&a.event===b.event;

export function notesFromSnapshot(value,matchIds){
  if(value===undefined) return new Map();
  if(!value||typeof value!=='object'||Array.isArray(value)) throw Error('Invalid match notes.');
  const notes=new Map();
  for(const [id,fields] of Object.entries(value)){
    if(!matchIds.has(id)) throw Error('Invalid match notes.');
    notes.set(id,normalizeMatchNotes(fields));
  }
  return notes;
}

export function matchNoteEdits(saved,pending,snapshot={}){
  return [...pending].map(([id,after])=>{
    // Preserve the legacy field when the browser is paired with an older watch.
    const team=typeof snapshot[id]?.team==='string'?snapshot[id].team:'';
    return {id,before:{...(saved.get(id)||EMPTY_MATCH_NOTES),team},after:{...after,team}};
  });
}
/** Catalog edits never select a match opponent. Match reassignment is a separate opt-in. */
export function addName(catalog,value){
 const n=name(value),found=catalog.find(e=>e.name.toLowerCase()===n.toLowerCase());
 if(found){found.archived=false;return found;}
 if(catalog.length>=1000)throw Error('Opponent storage is full');
 const entry={id:crypto.randomUUID(),name:n,archived:false};catalog.push(entry);return entry;
}
export function incomingPayload({snapshot,catalog,current,changeCurrent,csv,sessionId,myName}){
 const out={schema:1,sessionId,baseDirectory:snapshot.baseDirectory,currentMatchId:snapshot.currentMatchId,currentOpponentId:changeCurrent?current:(snapshot.currentOpponentId||''),changeCurrentOpponent:!!changeCurrent,opponents:catalog,csv};if(snapshot.supportsYourName&&typeof myName==='string'&&myName!==(snapshot.myName||'')){out.changeMyName=true;out.myName=myName;out.baseMyName=snapshot.myName||'';}return out;
}
export function checkRetirementSupport(snapshot,rows){if(!snapshot.supportsRetirement&&rows.some(r=>r[23]))throw Error('Update Keep Score on your watch to v0.11.0 or newer before importing retired matches.');}

export function byPlayer(rows, role) {
  const groups=new Map();
  for(const row of rows) {
    const columns=role==='partner'?(row[0]==='3'&&row[32]?[32]:[]):[4,...(row[0]==='3'&&row[34]?[34]:[])];
    for(const index of columns) {
      const id=row[index]||'';
      if(!groups.has(id)) groups.set(id,{fallback:row[index+1]||'',rows:[]});
      groups.get(id).rows.push(row);
    }
  }
  return [...groups].map(([id,group])=>({id,fallback:group.fallback,...statistics(group.rows)}));
}
export const opponentStatistics=rows=>byPlayer(rows,'opponent');

export function matchesPerson(row,id,role='all') {
  if(id==='*') return true;
  if(role==='opponent') return (row[4]||'')===id || Boolean(id&&row[0]==='3'&&row[34]===id);
  if(role==='partner') return Boolean(id&&row[0]==='3'&&row[32]===id);
  return id?participants(row).includes(id):participants(row).length===0;
}
const recordedDays=new WeakMap();
function recordedDay(row) {
  let entry=recordedDays.get(row);
  if(!entry||entry.start!==row[7]||entry.zone!==row[9]||entry.date!==row[2]) {
    entry={start:row[7],zone:row[9],date:row[2],day:localDate(row)};
    recordedDays.set(row,entry);
  }
  return entry.day;
}
export function filteredRows(rows,{id='*',role='all',outcome='all',from='',to='',event=null,eventFor=()=>''}={}) {
  if(from&&to&&from>to) throw Error('date');
  return rows.filter(row=>{
    if(!matchesPerson(row,id,role) || outcome!=='all' && (rowWinner(row)===0?'won':'lost')!==outcome) return false;
    if(event!==null&&eventFor(row)!==event) return false;
    if(!from&&!to) return true;
    const date=recordedDay(row);
    return (!from||date>=from)&&(!to||date<=to);
  });
}
export function last30Days(now=new Date()) {
  const first=new Date(now);
  first.setDate(first.getDate()-29);
  return {from:todayLocal(first),to:todayLocal(now)};
}

export function recordedTime(rows) {
  let milliseconds=0, timed=0;
  for(const row of rows) {
    const start=Date.parse(row[7]), finish=Date.parse(row[8]);
    if(!Number.isFinite(start)||!Number.isFinite(finish)||finish<start) continue;
    milliseconds+=finish-start;
    timed++;
  }
  return {timed,totalSeconds:timed?Math.floor(milliseconds/1000):null,averageSeconds:timed?Math.round(milliseconds/1000/timed):null};
}

function monthIndex(day) {
  const [year,month]=day.split('-').map(Number);
  return year*12+month-1;
}

export function monthlyStatistics(rows,{from='',to=''}={}) {
  const groups=new Map();
  for(const row of rows) {
    const month=monthIndex(recordedDay(row));
    if(!groups.has(month)) groups.set(month,[]);
    groups.get(month).push(row);
  }
  const records=new Map();
  for(const [month,matches] of groups) {
    records.set(month,{...statistics(matches),...recordedTime(matches)});
  }
  const months=[...records.keys()];
  return {
    records,
    first:from?monthIndex(from):months.length?Math.min(...months):null,
    last:to?monthIndex(to):months.length?Math.max(...months):null,
  };
}

export function newest(rows) {
  // Match the watch's recorded local day, known start time, then daily reference.
  return rows.map(row=>({row,day:recordedDay(row),start:row[7]?Date.parse(row[7]):0}))
    .sort((a,b)=>b.day.localeCompare(a.day)||b.start-a.start||Number(b.row[3])-Number(a.row[3]))
    .map(item=>item.row);
}
function eventOrder(locale) {
  const collator=new Intl.Collator(locale);
  return (a,b)=>a===b?0:!a?1:!b?-1:collator.compare(a,b)||(a<b?-1:1);
}
export function eventLabels(rows,eventFor,locale) {
  return [...new Set(rows.map(eventFor).filter(Boolean))].sort(eventOrder(locale));
}
export function sortResults(rows,eventFor,byEvent,locale) {
  const sorted=newest(rows);
  if(byEvent){
    const compare=eventOrder(locale);
    sorted.sort((a,b)=>compare(eventFor(a),eventFor(b)));
  }
  return sorted;
}
export function matchInsights(rows) {
  const ordered=newest(rows);
  let run=0, best=0, current=0, deciding=0, decidingWon=0, seconds=0, timed=0;
  const last=ordered.length?rowWinner(ordered[0]):null;
  for(const [index,row] of ordered.entries()) {
    const winner=rowWinner(row);
    if(index===current&&winner===last) current++;
    run=winner===0?run+1:0;best=Math.max(best,run);
    if(!row[23]&&Number(row[6])>1&&scorePairs(row).filter(Boolean).length===Number(row[6])) {
      deciding++;
      if(winner===0) decidingWon++;
    }
    if(row[10]!==''&&row[10]!=null) {seconds+=Number(row[10]);timed++;}
  }
  return {last,current,best,deciding,decidingWon,timed,averageSeconds:timed?Math.round(seconds/timed):null};
}
export function sportsByPlayer(rows) {
  const index=new Map();
  for(const row of rows) for(const id of participants(row)) {
    if(!index.has(id)) index.set(id,new Set());
    index.get(id).add(rowSport(row));
  }
  return index;
}

export function playersInLast30Days(rows, now = new Date()) {
  const matches = filteredRows(rows,last30Days(now));
  return new Set(matches.flatMap(participants));
}

export function recentPlayerRanks(rows) {
  const ranks = new Map();
  newest(rows).forEach((row, index) => {
    for (const id of participants(row)) if (!ranks.has(id)) ranks.set(id, index);
  });
  return ranks;
}

export function inScope(rows,sport,mode) {
  return sport==='all'?rows.filter(row=>mode==='all'||rowMode(row)===mode):sportRows(rows,sport,mode);
}
export function filterPlayers(catalog, rows) {
  const players=new Map(catalog.map(player=>[player.id,player]));
  for(const row of rows) for(const index of row[0]==='3'?[4,32,34]:[4]) {
    const id=row[index];
    if(id&&!players.has(id)) players.set(id,{id,name:row[index+1]||id,archived:false});
  }
  return [...players.values()];
}
