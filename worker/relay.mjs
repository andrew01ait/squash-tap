/** Only opaque encrypted payloads reach this service. Keys never leave URL fragments/devices. */
export const TTL=600_000,MAX_BODY=6_000_600;
const baseHeaders={"Cache-Control":"no-store, private","Content-Type":"application/json; charset=utf-8","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer","X-Frame-Options":"DENY"};
function response(value,status=200){return new Response(JSON.stringify(value),{status,headers:baseHeaders});}
async function hash(value){return [...new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(value)))].map(x=>x.toString(16).padStart(2,"0")).join("");}
function equal(a,b){if(typeof a!=="string"||a.length!==b.length)return false;let n=0;for(let i=0;i<a.length;i++)n|=a.charCodeAt(i)^b.charCodeAt(i);return n===0;}
function validCipher(value){return typeof value==="string"&&value.length<=(value.startsWith("2.")?6_000_000:1_500_000)&&/^[12]\.[A-Za-z0-9_-]{16}\.[A-Za-z0-9_-]{22,}$/.test(value);}
async function json(request){
  if(!request.headers.get("Content-Type")?.startsWith("application/json"))throw new Error("Use JSON");
  const reader=request.body?.getReader();if(!reader)throw new Error("Empty request");let size=0,chunks=[];
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>MAX_BODY)throw new Error("Request is too large");chunks.push(value);}}finally{await reader.cancel().catch(()=>{});}
  const bytes=new Uint8Array(size);let offset=0;for(const c of chunks){bytes.set(c,offset);offset+=c.length;}return JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
}
function objectKey(session,direction){return "transfers/"+String(session.expires_at).padStart(13,"0")+"/"+session.id+"/"+direction;}
export async function cleanup(env,now){
  // Access expires exactly at the deadline. Physical cleanup is bounded and runs on API traffic.
  const objects=await env.BUCKET.list({prefix:"transfers/",limit:100});
  const expired=objects.objects.filter(o=>Number(o.key.split("/")[1])<=now).map(o=>o.key);if(expired.length)await env.BUCKET.delete(expired);
  await env.DB.batch([env.DB.prepare("DELETE FROM transfers WHERE expires_at <= ?").bind(now),env.DB.prepare("DELETE FROM transfer_rates WHERE expires_at <= ?").bind(now)]);
}
export async function handleTransfer(request,env){
  if(!env.DB||!env.BUCKET)return response({error:"Transfer service unavailable"},503);
  const url=new URL(request.url),origin=request.headers.get("Origin");
  if(origin&&origin!==url.origin)return response({error:"Origin not allowed"},403);
  const path=url.pathname.replace(/\/$/,"");if(!/^\/api\/transfer(?:\/[a-f0-9]{32}(?:\/(?:outgoing|incoming))?)?$/.test(path))return response({error:"Not found"},404);
  const now=Date.now();await cleanup(env,now);
  try{
    if(path==="/api/transfer"){
      if(request.method!=="POST")return response({error:"Method not allowed"},405);
      const rate=await hash((request.headers.get("CF-Connecting-IP")||"local")+"|"+Math.floor(now/TTL));
      const count=await env.DB.prepare("INSERT INTO transfer_rates(id,count,expires_at) VALUES(?,1,?) ON CONFLICT(id) DO UPDATE SET count=count+1 RETURNING count").bind(rate,now+TTL).first();
      if(count.count>12)return response({error:"Too many transfers. Try again in ten minutes."},429);
      const total=await env.DB.prepare("SELECT count(*) AS n FROM transfers").first();if(total.n>=250)return response({error:"Transfer service is busy. Try again later."},503);
      const data=await json(request);
      if(!/^[a-f0-9]{32}$/.test(data.id)||!/^[-\w]{43}$/.test(data.ownerToken)||!/^[-\w]{43}$/.test(data.guestToken)||data.ownerToken===data.guestToken||!validCipher(data.cipher))return response({error:"Invalid transfer"},400);
      const session={id:data.id,expires_at:now+TTL};
      const exists=await env.DB.prepare("SELECT id FROM transfers WHERE id=?").bind(data.id).first();if(exists)return response({error:"Transfer already exists"},409);
      await env.BUCKET.put(objectKey(session,"out"),data.cipher,{httpMetadata:{contentType:"application/octet-stream"}});
      try{const created=await env.DB.prepare("INSERT INTO transfers(id,owner_hash,guest_hash,expires_at,incoming) SELECT ?,?,?,?,0 WHERE (SELECT count(*) FROM transfers)<250").bind(data.id,await hash(data.ownerToken),await hash(data.guestToken),session.expires_at).run();if(created.meta.changes!==1){await env.BUCKET.delete(objectKey(session,"out"));return response({error:"Transfer service is busy. Try again later."},503);}}
      catch(error){await env.BUCKET.delete(objectKey(session,"out"));throw error;}
      return response({id:data.id,expiresAt:session.expires_at,ttlSeconds:600},201);
    }
    const [, , ,id,action]=path.split("/");
    const session=await env.DB.prepare("SELECT * FROM transfers WHERE id=? AND expires_at>?").bind(id,now).first();
    if(!session)return response({error:"Transfer expired or ended. Start a new transfer on the watch."},404);
    const token=request.headers.get("Authorization")?.match(/^Bearer ([-\w]{43})$/)?.[1];if(!token)return response({error:"Transfer not authorized"},401);
    const digest=await hash(token),owner=equal(digest,session.owner_hash),guest=equal(digest,session.guest_hash);if(!owner&&!guest)return response({error:"Transfer not authorized"},401);
    if(!action&&request.method==="DELETE"){
      if(!owner)return response({error:"Watch confirmation required"},403);
      await env.BUCKET.delete([objectKey(session,"out"),objectKey(session,"in")]);await env.DB.prepare("DELETE FROM transfers WHERE id=?").bind(id).run();return response({ended:true});
    }
    if(request.method==="GET"&&(action==="outgoing"||action==="incoming")){
      if(action==="incoming"&&!owner||action==="outgoing"&&!guest)return response({error:"Role not allowed"},403);
      if(action==="incoming"&&session.incoming!==1)return response({error:"No changes received yet. Send them from the phone first."},409);
      const item=await env.BUCKET.get(objectKey(session,action==="outgoing"?"out":"in"));if(!item)return response({error:"Transfer content unavailable"},404);
      return response({cipher:await item.text(),expiresAt:session.expires_at});
    }
    if(action==="incoming"&&request.method==="POST"){
      if(!guest)return response({error:"Role not allowed"},403);const data=await json(request);if(!validCipher(data.cipher))return response({error:"Invalid transfer"},400);
      const changed=await env.DB.prepare("UPDATE transfers SET incoming=2 WHERE id=? AND incoming=0 AND expires_at>?").bind(id,now).run();if(changed.meta.changes!==1)return response({error:"Changes were already sent. Receive them on the watch or start a new transfer."},409);
      try{await env.BUCKET.put(objectKey(session,"in"),data.cipher,{httpMetadata:{contentType:"application/octet-stream"}});await env.DB.prepare("UPDATE transfers SET incoming=1 WHERE id=?").bind(id).run();}
      catch(error){await env.DB.prepare("UPDATE transfers SET incoming=0 WHERE id=? AND incoming=2").bind(id).run();throw error;}
      return response({ready:true},201);
    }
    return response({error:"Method not allowed"},405);
  }catch(error){return response({error:error instanceof SyntaxError?"Invalid JSON":/too large|Use JSON|Empty request/.test(String(error))?error.message:"Transfer request could not be completed"},400);}
}
export default {fetch:handleTransfer};
