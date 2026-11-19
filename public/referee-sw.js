import {storage, uploadToken, synchronize, transport, isClosed, dirty} from './referee-store.mjs';

const cacheName='keep-score-referee-shell-3.2';
const shell=['/referee.html','/referee-join.html','/referee-open.mjs','/match-web.css','/match-sheet.mjs','/score-outcome.css','/favicon.svg','/referee.css','/referee.mjs','/referee-store.mjs','/referee-scoring.mjs','/sports.mjs','/locale.mjs'];
const paths=new Set(shell);
self.addEventListener('install',event=>{
  event.waitUntil((async()=>{
    const cache=await caches.open(cacheName);
    await Promise.all(shell.map(async path=>{
      const response=await fetch(new Request(path,{cache:'reload',credentials:'same-origin'}));
      if(!response.ok)throw Error('Referee shell unavailable');
      // A host may canonicalize .html URLs. Store a direct response so that a
      // later offline navigation with redirect mode "manual" also succeeds.
      const direct=response.redirected
        ? new Response(response.body,{status:response.status,statusText:response.statusText,headers:response.headers}) : response;
      await cache.put(path,direct);
    }));
    await self.skipWaiting();
  })());
});
self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    for(const name of await caches.keys())if(name.startsWith('keep-score-referee-shell-')&&name!==cacheName)await caches.delete(name);
    await self.clients.claim();
  })());
});
self.addEventListener('fetch',event=>{
  const url=new URL(event.request.url);
  const path=event.request.mode==='navigate'&&['/referee','/referee/','/referee-join','/referee-join.html'].includes(url.pathname)?'/referee.html':url.pathname;
  if(event.request.method!=='GET'||url.origin!==self.location.origin||!paths.has(path))return;
  // Match documents and modules from one installed version. API responses and
  // request headers never enter the offline cache.
  event.respondWith((async()=>{
    const cache=await caches.open(cacheName);
    return await cache.match(path)||fetch(event.request);
  })());
});
self.addEventListener('sync',event=>{
  if(!/^ks-referee-[0-9a-f]{32}$/.test(event.tag))return;
  const id=event.tag.slice(11);
  event.waitUntil((async()=>{
    const record=await storage.read(id);
    if(!record||!dirty(record)||record.conflict||record.unavailable)return;
    const token=await uploadToken(id,record);
    if(!token)return;
    try { await synchronize(storage,id,transport(id,token)); }
    catch(error) {
      if(!isClosed(error))throw error;
      await storage.change(id,r=>r?{...r,unavailable:true}:undefined);
    } finally {
      for(const client of await self.clients.matchAll({type:'window'}))client.postMessage({type:'referee-update',id});
    }
  })());
});
