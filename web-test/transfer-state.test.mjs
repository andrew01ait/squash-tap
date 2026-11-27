import test from 'node:test';import assert from 'node:assert/strict';
import {addName,incomingPayload,checkRetirementSupport,opponentStatistics,initialView,recentPlayerRanks,playersInLast30Days,EMPTY_MATCH_NOTES,normalizeMatchNotes,notesFromSnapshot,matchNoteEdits} from '../public/transfer-state.mjs';
import {statistics,rowWinner,rowOutcome,writeCsv,parseCsv,RETIREMENT_HEADER} from '../public/interchange.mjs';
import {newest} from '../public/transfer-view.mjs';
const snapshot={baseDirectory:'base',currentMatchId:'last-completed',currentOpponentId:'alice',opponents:[{id:'alice',name:'Alice',archived:false}]};
const draft=(current,changeCurrent,catalog)=>incomingPayload({snapshot,catalog,current,changeCurrent,csv:'',sessionId:'00112233445566778899aabbccddeeff'});
const normal=['1','won','2026-11-07','1','alice','Alice','3','','','','','2','0','11','4','11','6','','','','','',''];
const retired=['2','retired','2026-11-06','1','bob','Bob','3','','','','','1','0','11','4','6','8','','','','','','','me'];
test('phone launch intent selects only the supported local tabs',()=>{for(const view of ['results','statistics','opponents','import'])assert.equal(initialView(view),view);});
test('recent player order includes doubles roles and uses the played date of backfilled results',()=>{
  const latest=[...normal];latest[0]='3';latest[2]='2026-11-24';latest[4]='rival';latest[32]='partner';latest[34]='second';
  const older=[...normal];older[2]='2020-01-01';older[4]='early';
  const ranks=recentPlayerRanks([latest,older]);
  assert.equal(ranks.get('rival'),0);assert.equal(ranks.get('partner'),0);assert.equal(ranks.get('second'),0);assert.equal(ranks.get('early'),1);
});
test('last 30 days includes both boundaries and all doubles roles using the recorded local date',()=>{
  const on=(day,id)=>{const row=[...normal];row[2]=day;row[4]=id;return row;};
  const team=on('2026-10-28','opponent');team[0]='3';team[32]='partner';team[34]='second';
  const late=on('2026-10-27','local');late[7]='2026-10-27T23:30:00Z';late[9]='Europe/Vienna';
  const rows=[on('2026-10-27','too-old'),team,on('2026-11-26','today'),on('2026-11-27','tomorrow'),late];
  assert.deepEqual(playersInLast30Days(rows,new Date(2026,10,26)),new Set(['opponent','partner','second','local','today']));
});
test('old links and untrusted phone launch values fall back to results',()=>{for(const value of [undefined,null,'','../settings','<script>','opponents&admin=true',{},['import']])assert.equal(initialView(value),'results');});
test('adding multiple names preserves the previous match assignment in the outgoing payload',()=>{const catalog=structuredClone(snapshot.opponents);const first=addName(catalog,'Bob'),last=addName(catalog,'Charlie');assert.equal(catalog.length,3);const p=draft(last.id,false,catalog);assert.equal(p.currentOpponentId,'alice');assert.equal(p.changeCurrentOpponent,false);assert.notEqual(first.id,last.id);assert.deepEqual(snapshot.opponents,[{id:'alice',name:'Alice',archived:false}]);});
test('adding to an unnamed match never silently names that match',()=>{const base={...snapshot,currentOpponentId:''},catalog=[];const last=addName(catalog,'Alice');const p=incomingPayload({snapshot:base,catalog,current:last.id,changeCurrent:false,csv:'',sessionId:'session'});assert.equal(p.currentOpponentId,'');});
test('restoring an existing name is a catalog edit and does not assign a match',()=>{const catalog=[{id:'bob',name:'Bob',archived:true}];assert.equal(addName(catalog,' Bob ').id,'bob');assert.equal(catalog.length,1);assert.equal(catalog[0].archived,false);assert.equal(draft('bob',false,catalog).currentOpponentId,'alice');});
test('explicit match reassignment and explicit clearing remain supported',()=>{assert.equal(draft('bob',true,[]).currentOpponentId,'bob');assert.equal(draft('',true,[]).currentOpponentId,'');assert.equal(draft('bob',true,[]).changeCurrentOpponent,true);});
test('retirement winner overrides set lead and partial sets are not counted',()=>{assert.equal(rowWinner(retired),1);assert.match(rowOutcome(retired),/You lost.*Me retired/);const s=statistics([normal,retired]);assert.deepEqual(s,{played:2,won:1,lost:1,setsFor:3,setsAgainst:0,pointsFor:39,pointsAgainst:22,gamesFor:0,gamesAgainst:0,unknownPointMatches:0,winRate:50,balance:0});});
test('head-to-head groups by stable player identity including unnamed and removed names',()=>{const unnamed=[...normal];unnamed[1]='unnamed';unnamed[4]='';unnamed[5]='';const groups=opponentStatistics([normal,retired,unnamed]);assert.equal(groups.length,3);assert.equal(groups.find(g=>g.id==='bob').lost,1);assert.equal(groups.find(g=>g.id==='alice').won,1);assert.equal(groups.find(g=>g.id==='').won,1);});
test('empty overall history has zero totals without a made-up win percentage',()=>{assert.equal(statistics([]).played,0);assert.equal(statistics([]).winRate,null);assert.deepEqual(opponentStatistics([]),[]);});
test('retirement CSV is gated for older watches while ordinary CSV still works',()=>{assert.throws(()=>checkRetirementSupport({},[retired]),/v0.11.0/);assert.doesNotThrow(()=>checkRetirementSupport({},[normal]));assert.doesNotThrow(()=>checkRetirementSupport({supportsRetirement:true},[retired]));});
test('schema-two retired history roundtrips and invalid partial set progression is rejected',()=>{const csv=writeCsv([normal,retired]);assert.ok(csv.startsWith('\ufeff'+RETIREMENT_HEADER));const rows=parseCsv(csv);assert.equal(rows[1][23],'me');assert.equal(rows[0][0],'2');const invalid=[...retired];invalid[13]='6';invalid[14]='5';assert.throws(()=>parseCsv(writeCsv([invalid])),/progression/);const impossible=[...retired];impossible[15]='12';impossible[16]='3';assert.throws(()=>parseCsv(writeCsv([impossible])),/set score/);});
test('all-match history sorts newest first without mutating the underlying snapshot',()=>{const rows=[retired,normal];assert.deepEqual(newest(rows).map(r=>r[1]),['won','retired']);assert.equal(rows[0][1],'retired');});

import {byPlayer,filteredRows,matchesPerson,matchInsights} from '../public/transfer-state.mjs';
import {tx,momentText,durationText,errorText} from '../public/transfer-copy.mjs';
import {readFileSync} from 'node:fs';
const doubleRow=(id,opponent,partner,second)=>['3',id,'2026-11-10','1',opponent,opponent,'3','','','','','2','0','6','1','6','1','','','','','','','','tennis','doubles','tennis,1,6,advantage,0','','','','','-1,-1,-1,-1;-1,-1,-1,-1',partner,partner,second,second];
test('a doubles player can appear as teammate and adversary in distinct filtered results',()=>{
  const a=doubleRow('A','Lee','Sal','Jo'),b=doubleRow('B','Jo','Lee','Sal');
  assert.deepEqual(filteredRows([a,b],{id:'Lee',role:'opponent'}),[a]);
  assert.deepEqual(filteredRows([a,b],{id:'Lee',role:'partner'}),[b]);
  assert.equal(byPlayer([a,b],'opponent').find(p=>p.id==='Sal').fallback,'Sal');
  assert.equal(byPlayer([a,b],'partner').find(p=>p.id==='Sal').played,1);
  assert.equal(filteredRows([a,b],{id:'Sal',role:'all'}).length,2);
  assert.equal(filteredRows([a,b],{id:'Lee',outcome:'lost'}).length,0);
  const unnamed=doubleRow('U','','Sal','Jo');
  assert.equal(matchesPerson(unnamed,'','opponent'),true);
  assert.equal(matchesPerson(unnamed,'','all'),false);
});
test('phone journal filters intersect and order by the recorded local day and time',()=>{
  const late=[...normal];late[1]='late';late[7]='2026-11-07T23:30:00Z';late[9]='GMT+02:00';
  const early=[...normal];early[1]='early';early[7]='2026-11-08T01:00:00Z';early[9]='Europe/Vienna';early[2]='2026-11-08';
  const other=[...retired];other[2]='2026-11-08';
  assert.deepEqual(newest(filteredRows([late,early,other],{id:'alice',from:'2026-11-08',to:'2026-11-08',outcome:'won'})).map(r=>r[1]),['early','late']);
  assert.throws(()=>filteredRows([],{from:'2026-11-08',to:'2026-11-07'}),/date/);
});
test('streaks use actual outcomes; deciders exclude retirements and averages exclude untimed matches',()=>{
  const full=[...normal];full[15]='7';full[16]='11';full[17]='11';full[18]='1';full[12]='1';full[3]='2';full[10]='200';
  const loss=[...retired];loss[2]=normal[2];loss[3]='3';loss[10]='0';
  const data=matchInsights([full,loss,normal]);
  assert.deepEqual(data,{last:1,current:1,best:2,deciding:1,decidingWon:1,timed:2,averageSeconds:100});
  assert.equal(matchInsights([]).averageSeconds,null);
});
test('all static phone labels and fixed-offset dates render in all four supported languages',()=>{
  const source=readFileSync(new URL('../public/transfer.html',import.meta.url),'utf8');
  const labels=[...source.matchAll(/data-(?:i|aria|placeholder)="([^"]+)"/g)].map(m=>m[1]);
  const prior=Object.getOwnPropertyDescriptor(globalThis,'navigator');
  try {
    for(const [lang,expected] of [['en','Matches'],['de','Partien'],['fr','Matchs'],['es','Partidos']]) {
      Object.defineProperty(globalThis,'navigator',{value:{languages:[lang]},configurable:true});
      assert.equal(tx('Matches'),expected);
      for(const label of labels) assert.ok(tx(label),label);
      const row=[...normal];row[7]='2026-11-07T23:30:00Z';row[9]='GMT+02:00';
      assert.ok(momentText(row).includes('30'));assert.ok(durationText(61).includes('1'));
      assert.ok(errorText('CSV row 4: Invalid rules').includes('4'));
    }
  }finally{if(prior)Object.defineProperty(globalThis,'navigator',prior);else delete globalThis.navigator;}
});

import {inScope,filterPlayers} from '../public/transfer-state.mjs';
test('all-sports browsing respects mode and can filter historical identities outside the current roster',()=>{
  const team=doubleRow('match','Lee','Sal','Jo'),rows=[team,normal],roster=[{id:'Lee',name:'Lee Smith',archived:true}];
  assert.deepEqual(inScope(rows,'all','doubles'),[team]);
  assert.deepEqual(inScope(rows,'all','all'),rows);
  assert.deepEqual(inScope(rows,'squash','all'),[normal]);
  const options=filterPlayers(roster,rows);
  assert.equal(options.length,4);
  assert.deepEqual(options.find(e=>e.id==='Lee'),roster[0]);
  assert.equal(options.find(e=>e.id==='Jo').name,'Jo');
  assert.equal(roster.length,1);
});
test('journal notes preserve newlines and round-trip assignment, edits and clearing by match ID',()=>{
  const original=normalizeMatchNotes({text:' Watch the backhand\r\n📝 Second line ',team:' Club Nord ',event:'Landesliga'});
  assert.deepEqual(original,{text:'Watch the backhand\n📝 Second line',team:'Club Nord',event:'Landesliga'});
  const saved=notesFromSnapshot({'match-id':original},new Set(['match-id','other']));
  const added=normalizeMatchNotes({text:'',team:'',event:'Tuesday tournament'});
  assert.deepEqual(matchNoteEdits(saved,new Map([['match-id',EMPTY_MATCH_NOTES],['other',added]])),[
    {id:'match-id',before:original,after:EMPTY_MATCH_NOTES},
    {id:'other',before:EMPTY_MATCH_NOTES,after:added}
  ]);
  assert.equal(notesFromSnapshot(undefined,new Set()).size,0);
  assert.throws(()=>notesFromSnapshot({'wrong-id':original},new Set(['match-id'])));
  assert.throws(()=>normalizeMatchNotes({...original,text:'x'.repeat(601)}));
  assert.throws(()=>normalizeMatchNotes({...original,team:'club\nteam'}));
  assert.throws(()=>normalizeMatchNotes({...original,event:'\ud800'}));
});
