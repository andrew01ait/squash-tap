import test,{beforeEach,afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import {createRequire} from 'node:module';
import {openSnapshot,roomKey,sealSnapshot} from '../public/referee-store.mjs';
const require=createRequire(import.meta.url),{Miniflare}=createRequire(require.resolve('wrangler/package.json'))('miniflare');
let worker,db;
const id='c'.repeat(32),writer='a'.repeat(43),reader='b'.repeat(43),admin='d'.repeat(43),joinCode='ABCD012345';
const cipher='1.'+'a'.repeat(16)+'.'+'c'.repeat(40),pairing=cipher+'p';
const seed={id,writer,reader,admin,joinCode,cipher,pairing,watchJoin:true,final:false,revision:0};
beforeEach(async()=>{
  worker=new Miniflare({modules:true,scriptPath:'web-test/rooms-entry.mjs',compatibilityDate:'2026-05-15',d1Databases:['DB']});
  db=await worker.getD1Database('DB');
  for(const f of readdirSync('drizzle').filter(f=>f.endsWith('.sql')).sort())
    for(const sql of readFileSync('drizzle/'+f,'utf8').split('--> statement-breakpoint'))if(sql.trim())await db.prepare(sql).run();
});
afterEach(async()=>worker.dispose());
const api=(path,method,body,token,headers={})=>worker.dispatchFetch('https://test.local/api/rooms'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...headers},...(body?{body:JSON.stringify(body)}:{})});
const create=(d=seed)=>api('','POST',d);
const join=()=>api('/join','POST',{code:'abcdO 12345'});
const score=(body,token=writer)=>api('/'+id,'PUT',body,token);
const fetchScore=(token=reader)=>api('/'+id,'GET',null,token);
test('only the referee writes, both watches see the same final after a delayed offline batch',async()=>{
 const key=await roomKey(Buffer.alloc(32,7).toString('base64url'));
 const data={schema:1,match:'M1|R1|3|1|',labels:['Lee','Alex','',''],startedAt:0,finishedAt:0,zone:'Europe/Vienna',final:false};
 assert.equal((await create({...seed,cipher:await sealSnapshot(key,id,data)})).status,201);
 assert.deepEqual(await (await join()).json(),{pairing});
 const final={...data,match:'M1|R1|3|1|BBBBBBBBBBB~1|BBBBBBBBBBB',final:true,finishedAt:23,startedAt:1};
 const update={baseRevision:0,revision:24,final:true,cipher:await sealSnapshot(key,id,final)};
 assert.equal((await score(update,reader)).status,403);
 assert.equal((await score(update,admin)).status,403);
 const first=await (await score(update)).json(),again=await (await score(update)).json();
 assert.deepEqual(again,first);
 const stored=await (await fetchScore()).json();
 assert.equal(stored.final,true);assert.deepEqual(await openSnapshot(key,id,stored.cipher),final);
 assert.deepEqual(await (await fetchScore(writer)).json(),stored);
 assert.equal((await score({...update,baseRevision:24,revision:25,final:false})).status,409);
 assert.ok(first.expiresAt-Date.now()>13*86400000);
});
test('simultaneous writers with identical base revision cannot erase another accepted score',async()=>{
 await create();const update={baseRevision:0,revision:2,cipher,final:false};
 const all=await Promise.all([score(update),score({...update,cipher:cipher+'z',revision:3})]);
 assert.deepEqual(all.map(x=>x.status).sort(),[200,409]);
 assert.equal((await score({...update,baseRevision:0,revision:7})).status,409);
});
test('creating after a lost response is idempotent and a pairing-code collision cannot replace another match',async()=>{
 const first=await (await create()).json();const second=await (await create()).json();assert.deepEqual(first,second);
 assert.equal((await create({...seed,id:'9'.repeat(32)})).status,409);
 assert.equal((await create({...seed,admin:'e'.repeat(43)})).status,409);
 const row=await db.prepare('SELECT * FROM match_rooms').first();
 for(const secret of [admin,reader,writer,joinCode])assert.equal(Object.values(row).includes(secret),false);
 assert.equal(row.cipher,cipher);assert.equal(row.pairing,pairing);
});
test('only the host can enable codes or revoke and previously paired readers survive code hiding',async()=>{
 await create({...seed,watchJoin:false});assert.equal((await join()).status,404);
 for(const token of [reader,writer]){assert.equal((await api('/'+id,'PATCH',{watchJoin:true},token)).status,403);assert.equal((await api('/'+id,'DELETE',null,token)).status,403);}
 await api('/'+id,'POST',{watchJoin:true},admin);assert.equal((await join()).status,200);
 await api('/'+id,'PATCH',{watchJoin:false},admin);assert.equal((await join()).status,404);assert.equal((await fetchScore()).status,200);
 await api('/'+id,'DELETE',null,admin);assert.equal((await fetchScore()).status,404);
});
test('expired rooms, unpaired capabilities, oversized updates, cross-origin requests and room enumeration are rejected',async()=>{
 await create();assert.equal((await fetchScore('x'.repeat(43))).status,401);assert.equal((await api('','GET')).status,405);
 assert.equal((await score({revision:1,baseRevision:0,cipher:'x'.repeat(120000),final:false})).status,400);
 assert.equal((await api('/join','POST',{code:joinCode},null,{Origin:'https://example.org'})).status,403);
 assert.match((await fetchScore()).headers.get('Cache-Control'),/no-store/);
 await db.prepare('UPDATE match_rooms SET expires_at=0').run();assert.equal((await fetchScore()).status,404);assert.equal((await join()).status,404);
});
