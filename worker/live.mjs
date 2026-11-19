/** An unlisted, read-only live scoreboard. Only encrypted snapshots are retained. */
export const LIVE_TTL=8*60*60*1000,LIVE_FINAL_TTL=10*60*1000,LIVE_MAX_BODY=48000;
const headers={'Cache-Control':'no-store, private','Content-Type':'application/json; charset=utf-8','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','X-Frame-Options':'DENY'};
const reply=(data,status=200)=>new Response(JSON.stringify(data),{status,headers});
const hash=async s=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))].map(n=>n.toString(16).padStart(2,'0')).join('');
function equal(a,b){if(typeof a!=='string'||a.length!==b.length)return false;let v=0;for(let i=0;i<a.length;i++)v|=a.charCodeAt(i)^b.charCodeAt(i);return v===0;}
const cipher=c=>typeof c==='string'&&c.length<=32000&&/^1\.[\w-]{16}\.[\w-]{22,}$/.test(c);
const validCompletion=d=>d.completedAt===undefined||Number.isSafeInteger(d.completedAt)&&d.completedAt>=0;
async function json(r){if(!r.headers.get('Content-Type')?.startsWith('application/json'))throw Error('Use JSON');const reader=r.body?.getReader();if(!reader)throw Error('Empty request');const parts=[];let n=0;try{while(true){const {done,value}=await reader.read();if(done)break;n+=value.length;if(n>LIVE_MAX_BODY)throw Error('Request is too large');parts.push(value);}}finally{await reader.cancel().catch(()=>{});}const bytes=new Uint8Array(n);let i=0;for(const p of parts){bytes.set(p,i);i+=p.length;}return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));}
export async function handleLive(request,env){
 if(!env.DB)return reply({error:'Live score service unavailable'},503);
 const url=new URL(request.url),origin=request.headers.get('Origin');if(origin&&origin!==url.origin)return reply({error:'Origin not allowed'},403);
 const path=url.pathname.replace(/\/$/,'');if(!/^\/api\/live(?:\/[a-f0-9]{32})?$/.test(path))return reply({error:'Not found'},404);
 const now=Date.now();if(path==='/api/live'&&request.method==='POST')await env.DB.batch([env.DB.prepare('DELETE FROM live_games WHERE expires_at<=?').bind(now),env.DB.prepare('DELETE FROM transfer_rates WHERE expires_at<=?').bind(now)]);
 try{
  if(path==='/api/live'){
   if(request.method!=='POST')return reply({error:'Method not allowed'},405);
   const rate=await hash('live|'+(request.headers.get('CF-Connecting-IP')||'local')+'|'+Math.floor(now/600000));
   const count=await env.DB.prepare('INSERT INTO transfer_rates(id,count,expires_at) VALUES(?,1,?) ON CONFLICT(id) DO UPDATE SET count=count+1 RETURNING count').bind(rate,now+600000).first();if(count.count>12)return reply({error:'Too many live links. Try again in ten minutes.'},429);
   const d=await json(request);if(!/^[a-f0-9]{32}$/.test(d.id)||!/^[-\w]{43}$/.test(d.ownerToken)||!/^[-\w]{43}$/.test(d.viewerToken)||d.ownerToken===d.viewerToken||!cipher(d.cipher)||!validCompletion(d))return reply({error:'Invalid live scoreboard'},400);
   if(await env.DB.prepare('SELECT id FROM live_games WHERE id=?').bind(d.id).first())return reply({error:'Live link already exists'},409);
   const activeExpires=now+LIVE_TTL,expires=d.completedAt>0?Math.min(now,d.completedAt)+LIVE_FINAL_TTL:activeExpires;const result=await env.DB.prepare('INSERT INTO live_games(id,owner_hash,viewer_hash,expires_at,active_expires_at,revision,cipher,updated_at) SELECT ?,?,?,?,?,1,?,? WHERE (SELECT count(*) FROM live_games)<250').bind(d.id,await hash(d.ownerToken),await hash(d.viewerToken),expires,activeExpires,d.cipher,now).run();
   if(result.meta.changes!==1)return reply({error:'Live score service is busy'},503);return reply({id:d.id,expiresAt:expires,revision:1},201);
  }
  const id=path.split('/')[3],row=await env.DB.prepare('SELECT * FROM live_games WHERE id=? AND expires_at>?').bind(id,now).first();if(!row)return reply({error:'This live link has ended or expired'},404);
  const token=request.headers.get('Authorization')?.match(/^Bearer ([-\w]{43})$/)?.[1];if(!token)return reply({error:'Live link not authorized'},401);const digest=await hash(token),owner=equal(digest,row.owner_hash),viewer=equal(digest,row.viewer_hash);if(!owner&&!viewer)return reply({error:'Live link not authorized'},401);
  if(request.method==='GET'){if(!viewer)return reply({error:'Audience link required'},403);return reply({cipher:row.cipher,revision:row.revision,updatedAt:row.updated_at,expiresAt:row.expires_at});}
  if(!owner)return reply({error:'Only the scoring watch can change this scoreboard'},403);
  if(request.method==='DELETE'){await env.DB.prepare('DELETE FROM live_games WHERE id=?').bind(id).run();return reply({ended:true});}
  if(request.method==='PUT'){const d=await json(request);if(!Number.isSafeInteger(d.revision)||d.revision<2||d.revision>2147483647||!cipher(d.cipher)||!validCompletion(d))return reply({error:'Invalid score update'},400);
   const completion=d.completedAt??null,deadline=d.completedAt>0?Math.min(now,d.completedAt)+LIVE_FINAL_TTL:0;
   const changed=await env.DB.prepare('UPDATE live_games SET cipher=?,revision=?,updated_at=?,active_expires_at=COALESCE(active_expires_at,expires_at),expires_at=CASE WHEN ? IS NULL THEN expires_at WHEN ?=0 THEN COALESCE(active_expires_at,expires_at) WHEN expires_at=COALESCE(active_expires_at,expires_at) THEN ? ELSE MIN(expires_at,?) END WHERE id=? AND revision<? AND expires_at>? RETURNING expires_at').bind(d.cipher,d.revision,now,completion,completion,deadline,deadline,id,d.revision,now).first();
   if(!changed)return reply({error:'A newer score is already stored'},409);return reply({updated:true,revision:d.revision,expiresAt:changed.expires_at});}
  return reply({error:'Method not allowed'},405);
 }catch(e){return reply({error:e instanceof SyntaxError?'Invalid JSON':/too large|Use JSON|Empty request/.test(String(e))?e.message:'Live score request could not be completed'},400);}
}
