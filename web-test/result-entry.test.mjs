import test from 'node:test';
import assert from 'node:assert/strict';
import {defaultRules,resultFromEntry,entryChanges,bestOptions} from '../public/result-entry.mjs';
import {writeCsv,parseCsv,statistics} from '../public/interchange.mjs';
import {SPORTS,scorePairs} from '../public/sports.mjs';
const config={id:'new-match',date:'2026-11-10',today:'2026-11-18',best:3,players:[],rules:defaultRules('squash'),scores:[[11,6],[11,8]]};
test('a completed browser result for every sport round-trips with its scoring rules, roles and date',()=>{
  for(const sport of SPORTS) {
    const rules=defaultRules(sport),score=[rules.target,3],players=rules.doubles?[{id:'o',name:'Rival'},{id:'p',name:'Partner'},{id:'r',name:'Other'}]:[];
    const row=resultFromEntry({...config,rules,players,scores:[score,score]});
    assert.deepEqual(parseCsv(writeCsv([row])),[row]);assert.equal(row[2],config.date);assert.equal(row[7],'');
    assert.equal(row[11],'2');assert.equal(row[12],'0');if(rules.doubles)assert.equal(row[34],'r');
  }
  const tennis={...defaultRules('tennis'),finalTie:true};
  const row=resultFromEntry({...config,rules:tennis,scores:[[6,2],[5,7],[10,8]]});
  assert.deepEqual(scorePairs(row).filter(Boolean),[[6,2],[5,7],[10,8]]);assert.equal(statistics([row]).gamesFor,11);
  const tt=resultFromEntry({...config,rules:defaultRules('table-tennis'),best:7,scores:[[11,1],[1,11],[11,1],[1,11],[11,1],[1,11],[11,2]]});
  assert.equal(tt[29],'11');assert.ok(bestOptions('pickleball').includes(1));
});
test('invalid scores, unfinished matches, duplicates, gaps, dates, retirement and trailing games are rejected',()=>{
  for(const scores of [[[10,8],[11,8]],[[11,'']],[[11,9]],[['',''],[11,1],[11,2]],[[11,5],[11,4],[11,1]]])
    assert.throws(()=>resultFromEntry({...config,scores}));
  for(const date of ['2026-02-30','2026-11-19','1899-12-31'])assert.throws(()=>resultFromEntry({...config,date}));
  assert.throws(()=>resultFromEntry({...config,rules:defaultRules('padel'),scores:[[6,0],[6,0]],players:[{id:'p',name:'Pat'},{id:'p',name:'Pat'}]}));
  assert.throws(()=>resultFromEntry({...config,retired:'me'}));
  assert.equal(resultFromEntry({...config,retired:'me',scores:[[11,6],[3,2]]})[23],'me');
});
test('correcting historical tennis retains matching rally/tie-break details and recorded timing',()=>{
  const rules=defaultRules('tennis'),old=resultFromEntry({...config,rules,scores:[[7,6],[6,3]]});
  old[31]='48,43,8,6,-1,-1;31,22,-1,-1,-1,-1';
  [old[7],old[8],old[9],old[10]]=['2026-11-09T23:31:00Z','2026-11-10T00:31:00Z','Europe/Vienna','3600'];
  const corrected=resultFromEntry({...config,rules,original:old,scores:[[7,6],[6,4]]});
  assert.equal(corrected[1],old[1]);assert.deepEqual(corrected.slice(7,11),old.slice(7,11));
  assert.equal(corrected[31],'48,43,8,6,-1,-1;-1,-1,-1,-1,-1,-1');
  assert.equal(old[16],'3');assert.equal(corrected[16],'4');
  const reverted=resultFromEntry({...config,rules,original:corrected,baseline:old,scores:[[7,6],[6,3]]});
  assert.deepEqual(reverted,old);
});
test('mixed additions and corrections use distinct payloads and preserve the original match for conflict checks',()=>{
  const initial=resultFromEntry(config),next=resultFromEntry({...config,original:initial,scores:[[11,9],[11,8]]});
  const added=resultFromEntry({...config,id:'second'}),imported=resultFromEntry({...config,id:'file'});
  const batch=entryChanges(new Map([[initial[1],next],[added[1],added]]),new Map([[initial[1],initial]]),[imported]);
  assert.deepEqual(parseCsv(batch.csv).map(row=>row[1]),['file','second']);
  assert.deepEqual(parseCsv(batch.edits.before),[initial]);assert.deepEqual(parseCsv(batch.edits.after),[next]);
  assert.deepEqual(entryChanges(new Map(),new Map(),[]),{csv:'',edits:null});
});
