import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {RefereeMatch, applyAction, readSnapshot} from '../public/referee-scoring.mjs';
import {roomKey, sealSnapshot, openSnapshot, refereeLink, acceptRemote, dirty,
  updateLocal, synchronize, resolveConflict, RoomError} from '../public/referee-store.mjs';

const fixture=match=>({schema:1,match,labels:['A','B','',''],zone:'',startedAt:0,finishedAt:0,final:false});
const fixtureId='0123456789abcdef0123456789abcdef';
function seed(format,best=3,first=-1,other=-1,body){
  const service=[first,other,...Array(12).fill(-1)].join(',');
  const game=format.startsWith('squash')?'1|':format.startsWith('pickleball')?'4|'+format+'|'+first+','+other+'|L':'3|'+format+'|0|L';
  return ['M4','test',best,-1,format,service,body||game].join('|');
}
const squash=()=> 'M1|test|3|1|';
const trace=(m,path)=>{
  for(const action of path){
    if(action==='B'||action==='R')m.score(action==='R'?1:0);
    if(action==='U')m.undo();
    if(action==='N')m.next();
    if(action==='Q')m.retire(1);
  }
  return m;
};

test('phone results require an explicit finish, match timing and source metadata survive Undo',()=>{
  let a={...fixture(squash()),extra:'kept'};
  a=applyAction(a,{type:'score',side:0},1000,'Europe/Vienna');
  assert.equal(a.startedAt,1000);assert.equal(a.zone,'Europe/Vienna');
  assert.equal(applyAction(a,{type:'finish'}),null);
  a=applyAction(a,{type:'retire',side:1},2000);assert.equal(a.final,false);assert.equal(a.finishedAt,2000);
  a=applyAction(a,{type:'undo'});assert.equal(a.startedAt,1000);assert.equal(a.finishedAt,0);
  a=applyAction(a,{type:'retire',side:0},3000);
  a=applyAction(a,{type:'finish'},4000);assert.equal(a.finishedAt,3000);assert.ok(a.final);
  assert.equal(a.extra,'kept');assert.equal(applyAction(a,{type:'undo'}),null);
  assert.throws(()=>readSnapshot({...fixture(squash()),final:true}));
});
test('retirement before any rally keeps the start unknown, as do legacy active matches',()=>{
  const a=applyAction(fixture(squash()),{type:'retire',side:1},222,'Europe/Vienna');
  assert.equal(a.startedAt,0);assert.equal(a.finishedAt,222);assert.equal(a.zone,'Europe/Vienna');
  let legacy=applyAction(fixture('M1|test|3|1|B'),{type:'score',side:1},222,'Europe/Vienna');
  assert.equal(legacy.startedAt,0);
  for(const action of [{type:'undo'},{type:'undo'},{type:'score',side:1}])legacy=applyAction(legacy,action,444);
  assert.equal(legacy.startedAt,0);
});
test('pickleball requires explicit starters and changes server on non-scoring rallies',()=>{
  const d=fixture(seed('pickleball,1,11,side-out,0'));
  assert.equal(applyAction(d,{type:'score',side:0}),null);
  const m=readSnapshot(applyAction(d,{type:'starters',first:2,other:3}));
  assert.equal(m.game.pNumber,2);assert.equal(m.server,2);m.score(1);assert.equal(m.server,3);assert.deepEqual(m.game.score,[0,0]);
  assert.equal(m.starters(0,1),false);m.undo();assert.equal(m.server,2);assert.ok(m.starters(0,1));
});
test('native and browser replay agree on real encoded scores, service, Undo and winners across all sports',()=>{
  const scenarios=[], push=(start,actions)=>scenarios.push([start,actions]);
  for(const entered of ['M1|test|3|1|BR','M2|test|3|1|1|BR','M3|test|3|-1|2|11|8~1|BR'])
    for(const path of ['', 'U','UUR','Q','QU'])push(entered,path);
  const alternation='BR'.repeat(30), squashOrTableWins='B'.repeat(11)+'N'+'R'.repeat(11)+'N'+'B'.repeat(11);
  for(const sport of ['squash','table-tennis','badminton','tennis','padel','pickleball']){
    const configs=sport==='pickleball'?['side-out','rally-2025','rally-2026']:sport==='padel'?['advantage','no-ad','star']:sport==='tennis'?['advantage','no-ad']:['advantage'];
    const kinds=sport==='padel'?[1]:sport==='squash'?[0]:[0,1];
    for(const doubles of kinds)for(const rule of configs){
      const nested=['tennis','padel'].includes(sport),target=nested?6:sport==='badminton'?15:11;
      const start=seed([sport,doubles,target,rule,Number(nested)].join(','),3,doubles?2:0,doubles?3:1);
      const ties='BBBBRRRR'.repeat(6)+'BR'.repeat(7)+'BB';
      const win='B'.repeat(nested?24:target),lose='R'.repeat(nested?24:target);
      for(const path of ['', 'BRRBR','BRRBRU',alternation+'BBNBR',win+'N'+lose+'NBRBR',win+'N'+lose+'N'+'B'.repeat(12)+'U',squashOrTableWins,ties+'NRRUQ','BBBRRRBRBRBB',win+'NU','BRQU'])
        push(start,path);
    }
  }
  const expected=scenarios.map(([start,path])=>{
    const m=trace(RefereeMatch.decode(start),path);
    return [m.encode(),m.game.score.join(','),m.sets.join(','),m.winner,m.server].join('\t');
  });
  const native=spawnSync('java',['-cp',process.env.REFEREE_JAVA_CLASSES||'build/test-classes','web-test/RefereeParity.java'],{
    input:scenarios.map(s=>s.join('\t')).join('\n'),encoding:'utf8',maxBuffer:8*1024*1024});
  assert.equal(native.status,0,native.stderr);
  assert.deepEqual(native.stdout.trimEnd().split('\n'),expected);
});

function db(initial){
  let record=structuredClone(initial);return {
    read:async()=>structuredClone(record),
    change:async(_id,fn)=>{const changed=fn(structuredClone(record));if(changed!==undefined)record={...changed,version:(record?.version||0)+1};return structuredClone(record);}
  };
}
const packet=(revision,cipher,final=false)=>({revision,cipher,final,expiresAt:1700000000000});
function server(initial){
  const api={head:initial,writes:[],get:async()=>({...api.head}),put:async p=>{
    if(api.head.revision!==p.baseRevision||api.head.final)throw new RoomError(409);
    api.writes.push({...p});api.head=packet(p.revision,p.cipher,p.final);return {expiresAt:api.head.expiresAt};
  }};
  return api;
}
async function edit(store,cipher,final=false){return updateLocal(store,fixtureId,()=>({cipher,final}));}
test('offline runs coalesce to one final snapshot, including after the local database is reopened',async()=>{
  const api=server(packet(0,'initial'));let store=db(acceptRemote(fixtureId,api.head));
  for(const [value,final] of [['point 1',false],['point 2',false],['undo',false],['winner',false],['confirmed',true]])await edit(store,value,final);
  store=db(await store.read());await synchronize(store,fixtureId,api);
  assert.equal(api.writes.length,1);assert.equal(api.head.revision,5);assert.equal(api.head.cipher,'confirmed');assert.ok(api.head.final);
  assert.equal(dirty(await store.read()),false);
});
test('an accepted update with a lost response is acknowledged before later offline rallies',async()=>{
  const api=server(packet(0,'zero')),store=db(acceptRemote(fixtureId,api.head));
  const put=api.put;api.put=async p=>{await put(p);throw Error('disconnected');};
  await edit(store,'first');await assert.rejects(synchronize(store,fixtureId,api));
  assert.equal((await store.read()).flight.cipher,'first');
  await edit(store,'later');api.put=put;await synchronize(db(await store.read()),fixtureId,api);
  assert.deepEqual(api.writes.map(w=>[w.baseRevision,w.revision,w.cipher]),[[0,1,'first'],[1,2,'later']]);
});
test('a failed upload with uncertain delivery retries the persisted ciphertext exactly',async()=>{
  const api=server(packet(0,'zero')),store=db(acceptRemote(fixtureId,api.head));const put=api.put;let lost;
  api.put=async p=>{lost=p;throw Error('offline');};
  await edit(store,'point');await assert.rejects(synchronize(store,fixtureId,api));
  api.put=put;await synchronize(store,fixtureId,api);assert.deepEqual(lost,api.writes[0]);
});
test('competing referee devices retain both branches and a confirmed remote outcome is immutable',async()=>{
  const api=server(packet(0,'zero')),store=db(acceptRemote(fixtureId,api.head));
  await edit(store,'ours');api.head=packet(1,'theirs');
  let r=await synchronize(store,fixtureId,api);assert.equal(r.cipher,'ours');assert.equal(r.conflict.cipher,'theirs');assert.equal(api.writes.length,0);
  assert.equal(await edit(store,'accidental'),null);
  await resolveConflict(store,fixtureId,true,r.conflict);await synchronize(store,fixtureId,api);assert.equal(api.head.cipher,'ours');assert.equal(api.head.revision,2);
  await edit(store,'next');api.head=packet(3,'final-elsewhere',true);r=await synchronize(store,fixtureId,api);
  await resolveConflict(store,fixtureId,true,r.conflict);assert.equal((await store.read()).conflict.cipher,'final-elsewhere');
  await resolveConflict(store,fixtureId,false,r.conflict);assert.equal((await store.read()).cipher,'final-elsewhere');
});
test('concurrent edits in two tabs apply once each, with a monotone shared local revision',async()=>{
  const store=db(acceptRemote(fixtureId,packet(0,'start')));
  await Promise.all(Array.from({length:8},()=>updateLocal(store,fixtureId,async r=>{await Promise.resolve();return {cipher:r.cipher+'B',final:false};})));
  const r=await store.read();assert.equal(r.revision,8);assert.equal(r.cipher,'start'+'B'.repeat(8));
});
test('stale network reads from another tab cannot roll back a newer acknowledgement',async()=>{
  const newer=packet(3,'new'),store=db(acceptRemote(fixtureId,newer)),api={get:async()=>packet(1,'old')};
  assert.equal((await synchronize(store,fixtureId,api)).cipher,'new');
});
test('AES-GCM authenticates each room ID and keeps every snapshot readable with its referee QR',async()=>{
  const raw=Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString('base64url');
  const k=await roomKey(raw),d=fixture(squash()),sealed=await sealSnapshot(k,fixtureId,d);
  assert.match(sealed,/^1\.[\w-]{16}\.[\w-]+$/);assert.deepEqual(await openSnapshot(k,fixtureId,sealed),d);
  await assert.rejects(openSnapshot(k,'b'.repeat(32),sealed));assert.equal(refereeLink('#id='+fixtureId+'&key=x&token=y'),null);
  assert.deepEqual(refereeLink('#id='+fixtureId+'&key='+raw+'&token='+raw),{id:fixtureId,key:raw,token:raw});
});
test('offline installation precaches every referee module and excludes every room response',()=>{
  const sw=readFileSync('public/referee-sw.js','utf8'),shell=sw.match(/const shell=\[([^\]]+)\]/)[1].match(/'[^']+'/g).map(p=>p.slice(2,-1));
  for(const name of shell){
    const source=readFileSync('public/'+name,'utf8');
    for(const path of source.matchAll(/from ['"]\.\/([^'"?]+)(?:\?[^'"]*)?['"]/g))assert.ok(shell.includes(path[1]),path[1]+' missing offline');
  }
  assert.ok(shell.includes('referee.html'));assert.ok(shell.every(s=>!s.startsWith('api')));
});
