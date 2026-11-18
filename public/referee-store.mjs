// IndexedDB stores only encrypted match payloads. A non-extractable local key wraps
// the upload capability for browsers that can finish queued uploads after tab closure.
const NAME='keep-score-referee-v1';
const encoder=new TextEncoder(), decoder=new TextDecoder();
const MAX_CIPHER=140000, keyName='outbox-wrapping-key';
const aad=(kind,id)=>encoder.encode('KeepScore:'+kind+':1:'+id);
const b64=bytes=>{
  let text='';for(let i=0;i<bytes.length;i+=8192)text+=String.fromCharCode(...bytes.subarray(i,i+8192));
  return btoa(text).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
};
function bytes(s){return Uint8Array.from(atob(s.replaceAll('-','+').replaceAll('_','/')),c=>c.charCodeAt(0));}
export async function roomKey(raw){return crypto.subtle.importKey('raw',bytes(raw),'AES-GCM',false,['encrypt','decrypt']);}
async function encrypt(key,context,plaintext) {
  const iv=crypto.getRandomValues(new Uint8Array(12));
  const out=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:context},key,encoder.encode(plaintext));
  return '1.'+b64(iv)+'.'+b64(new Uint8Array(out));
}
async function decrypt(key,context,cipher) {
  if(typeof cipher!=='string'||cipher.length>MAX_CIPHER||!/^1\.[\w-]{16}\.[\w-]{22,}$/.test(cipher))throw Error('Invalid payload');
  const [,iv,payload]=cipher.split('.');
  return decoder.decode(await crypto.subtle.decrypt({name:'AES-GCM',iv:bytes(iv),additionalData:context},key,bytes(payload)));
}
export const sealSnapshot=(key,id,data)=>encrypt(key,aad('room',id),JSON.stringify(data));
export async function openSnapshot(key,id,wire){return JSON.parse(await decrypt(key,aad('room',id),wire));}
export function refereeLink(hash) {
  const link=new URLSearchParams(hash.replace(/^#/,''));
  const id=link.get('id'),key=link.get('key'),token=link.get('token');
  const invite=link.get('invite');
  if(token&&invite)return null;
  if(!/^[a-f0-9]{32}$/.test(id||'')||![key,token||invite].every(s=>typeof s==='string'&&/^[\w-]{43}$/.test(s)))return null;
  return invite?{id,key,invite}:{id,key,token};
}

let database;
function openDB() {
  if(!database)database=new Promise((resolve,reject)=>{
    const request=indexedDB.open(NAME,1);
    request.onupgradeneeded=()=>{
      request.result.createObjectStore('rooms',{keyPath:'id'});
      request.result.createObjectStore('device');
    };
    request.onsuccess=()=>{
      request.result.onversionchange=()=>{request.result.close();database=undefined;};
      resolve(request.result);
    };
    request.onerror=()=>{database=undefined;reject(request.error);};
  });
  return database;
}
async function transact(storeName,mode,body) {
  const db=await openDB();
  return new Promise((resolve,reject)=>{
    let tx;
    try{tx=db.transaction(storeName,mode,{durability:'strict'});}catch{tx=db.transaction(storeName,mode);}
    let result;
    tx.oncomplete=()=>resolve(result);
    tx.onabort=tx.onerror=()=>reject(tx.error||Error('Local storage unavailable'));
    try{body(tx.objectStore(storeName),value=>{result=value;});}catch(error){tx.abort();reject(error);}
  });
}
export const storage={
  read:id=>transact('rooms','readonly',(s,set)=>{const r=s.get(id);r.onsuccess=()=>set(r.result);}),
  change:(id,fn)=>transact('rooms','readwrite',(s,set)=>{
    const r=s.get(id);
    r.onsuccess=()=>{
      const prior=r.result, next=fn(prior);
      if(next===undefined){set(prior);return;}
      if(next===null){s.delete(id);set(null);return;}
      next.version=(prior?.version||0)+1;s.put(next);set(next);
    };
  })
};
async function localKey(name,algorithm,usages) {
  const existing=await transact('device','readonly',(s,set)=>{const r=s.get(name);r.onsuccess=()=>set(r.result);});
  if(existing)return existing;
  const generated=await crypto.subtle.generateKey(algorithm,false,usages);
  return transact('device','readwrite',(s,set)=>{
    const r=s.get(name);
    r.onsuccess=()=>{if(!r.result)s.put(generated,name);set(r.result||generated);};
  });
}
const wrappingKey=()=>localKey(keyName,{name:'AES-GCM',length:256},['encrypt','decrypt']);
export async function deviceToken(id){
  const key=await localKey('referee-device-key',{name:'HMAC',hash:'SHA-256',length:256},['sign']);
  return b64(new Uint8Array(await crypto.subtle.sign('HMAC',key,encoder.encode('KeepScore:referee:'+id))));
}
export async function deviceTag(id,token){
  const hex=async text=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',encoder.encode(text))),b=>b.toString(16).padStart(2,'0')).join('');
  return '#'+(await hex('member|'+id+'|'+await hex(token))).slice(18,24).toUpperCase();
}
export async function rememberUploadToken(id,token) {
  const wrapped=await encrypt(await wrappingKey(),aad('referee-outbox',id),token);
  await storage.change(id,r=>r&&!r.wrappedToken&&!(r.final&&!dirty(r))?{...r,wrappedToken:wrapped}:undefined);
}
export async function uploadToken(id,record) {
  if(!record?.wrappedToken)return null;
  return decrypt(await wrappingKey(),aad('referee-outbox',id),record.wrappedToken);
}
export const dirty=r=>r.revision!==r.ackRevision||r.cipher!==r.ackCipher;
const same=(a,b)=>a&&b&&a.revision===b.revision&&a.cipher===b.cipher;
const packet=r=>({revision:r.revision,cipher:r.cipher,final:r.final});
export function acceptRemote(id,remote,previous={}) {
  return {...previous,id,...packet(remote),ackRevision:remote.revision,ackCipher:remote.cipher,
    expiresAt:remote.expiresAt,flight:null,conflict:null,unavailable:false,...(remote.final?{wrappedToken:null}:{})};
}
export class RoomError extends Error {constructor(status){super('Room request failed');this.status=status;}}
export function transport(id,token,invite) {
  const request=async(method,body)=>{
    const c=new AbortController(), timer=setTimeout(()=>c.abort(),9000);
    try{
      const res=await fetch('/api/rooms/'+id,{method,body:body?JSON.stringify(body):undefined,signal:c.signal,cache:'no-store',credentials:'omit',redirect:'error',
        headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})}});
      if(!res.ok)throw new RoomError(res.status);
      if(method==='PUT'){try{return await res.json();}catch{return {};}}
      const remote=await res.json();
      if(!Number.isSafeInteger(remote.revision)||remote.revision<0||!Number.isSafeInteger(remote.expiresAt)||typeof remote.final!=='boolean'||typeof remote.cipher!=='string'||remote.cipher.length>MAX_CIPHER)throw Error('Invalid response');
      return remote;
    }finally{clearTimeout(timer);}
  };
  async function get(){
    try{return await request('GET');}catch(error){if(!invite||error.status!==401)throw error;}
    const response=await fetch('/api/rooms/'+id+'/referees',{method:'POST',credentials:'omit',redirect:'error',cache:'no-store',
      signal:AbortSignal.timeout(9000),headers:{Authorization:'Bearer '+invite,'Content-Type':'application/json'},body:JSON.stringify({deviceToken:token})});
    if(!response.ok)throw new RoomError(response.status);
    return request('GET');
  }
  return {get,put:body=>request('PUT',body)};
}

// Each accepted tap uses a single compare-and-swap in the local database. Another
// tab may have finished encryption or processed an acknowledgement in the meantime.
export async function updateLocal(store,id,transform) {
  for(let i=0;i<30;i++){
    const current=await store.read(id);
    if(!current||current.conflict||current.unavailable)return null;
    const result=await transform(current);if(!result)return null;
    let applied=false;
    const next=await store.change(id,latest=>{
      if(latest?.version!==current.version)return;
      applied=true;
      return {...latest,...result,revision:latest.revision+1};
    });
    if(applied)return next;
  }
  throw Error('Concurrent local writes');
}
function acknowledge(record,flight,expiry) {
  const r={...record,ackRevision:flight.revision,ackCipher:flight.cipher,flight:null,conflict:null,
    expiresAt:Number.isSafeInteger(expiry)?expiry:record.expiresAt,unavailable:false};
  if(r.final&&!dirty(r))r.wrappedToken=null;
  return r;
}
export async function synchronize(store,id,api) {
  let remote;
  // The persisted flight also identifies an accepted PUT whose response was lost.
  // Reconcile it before sending newer local rallies, even after process death.
  for(let attempt=0;attempt<10;attempt++){
    let record=await store.read(id);
    if(!record)return null;
    if(!remote)remote=await api.get();
    record=await store.change(id,r=>{
      if(!r||remote.revision<r.ackRevision)return;
      if(r.flight&&same(r.flight,remote))return acknowledge(r,r.flight,remote.expiresAt);
      if(r.unavailable)return {...r,unavailable:false};
      if(!dirty(r)){
        if(!same(r,remote)||r.conflict||r.expiresAt!==remote.expiresAt)return acceptRemote(id,remote,r);
        return;
      }
      if(remote.revision!==r.ackRevision||remote.cipher!==r.ackCipher){
        // Another tab may have acknowledged a newer write after our GET.
        if(remote.revision<r.ackRevision)return;
        if(!same(r.conflict,remote))return {...r,conflict:remote,flight:null};
        return;
      }
      if(!r.flight&&!r.conflict)return {...r,flight:{...packet(r),baseRevision:r.ackRevision}};
    });
    if(!record||record.conflict||!dirty(record))return record;
    if(!record.flight||remote.revision<record.ackRevision){remote=null;continue;}
    const flight=record.flight;
    try{
      const answer=await api.put(flight);
      record=await store.change(id,r=>r&&same(r.flight,flight)?acknowledge(r,flight,answer?.expiresAt):undefined);
      if(!record||!dirty(record))return record;
      remote=null;
    }catch(error){
      if(error.status!==409)throw error;
      remote=null;
    }
  }
  return store.read(id);
}
export async function resolveConflict(store,id,keepLocal,expected) {
  return store.change(id,r=>{
    const remote=r?.conflict;
    if(!same(remote,expected))return;
    if(!keepLocal)return acceptRemote(id,remote,r);
    if(remote.final)return;
    return {...r,revision:Math.max(remote.revision,r.revision)+1,ackRevision:remote.revision,ackCipher:remote.cipher,flight:null,conflict:null,expiresAt:remote.expiresAt};
  });
}
export const isClosed=error=>[401,403,404,410].includes(error?.status);
export async function clearExpiredCopies(except) {
  const now=Date.now();
  return transact('rooms','readwrite',store=>{
    const cursor=store.openCursor();
    cursor.onsuccess=()=>{
      const c=cursor.result;if(!c)return;
      const r=c.value;
      if(r.id!==except&&!dirty(r)&&r.expiresAt<now)c.delete();
      c.continue();
    };
  });
}
