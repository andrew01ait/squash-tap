/** Development-only synthetic watch. This module is removed from production builds. */
import {handleLive} from '../worker/live.mjs';
import {handleTransfer} from '../worker/relay.mjs';
import {seal,open,b64,writeCsv} from '../public/interchange.mjs';
const fullBackup="{\"application\":\"Keep Score\",\"package\":\"at.andrew.squashtap\",\"schema\":1,\"createdAt\":\"2026-11-11T07:33:35.112601130Z\",\"payload\":\"S1NCMQAAAFtCNDpBQmROTVh4amRYSnlaVzUwTFdKaFkydDFjSHd6ZkRGOFFnQUFBQUFBQUFBQUFBR2h5WWJFMVFBQUFBQUFBQUFBQUFkRmRHTXZWVlJEQVFBQUFBQUFBQUFBAAAAn08xOkFBQUFBUUFrTXpWbE5HUm1NV0V0Tm1Ga1lTMDBNalV4TFRnM05UQXRORFJsTjJNMlpESXpZall5QUE1S3c3eHlaMlZ1SU8yZ3ZPMi91QUFBQUFBQkFBNWpkWEp5Wlc1MExXSmhZMnQxY0FBa016VmxOR1JtTVdFdE5tRmtZUzAwTWpVeExUZzNOVEF0TkRSbE4yTTJaREl6WWpZeQAAAAEAAAAHbXlfbmFtZQAAAAZBbmRyw6k\",\"sha256\":\"F3s7rVBVOXaPgU7d8raKMOXzf1wipO06-nysLKwtMiA\"}";
const owner='w'.repeat(43),guest='p'.repeat(43),key=b64(new Uint8Array(32).fill(7));
export async function preview(request,env){
 const url=new URL(request.url);
 if(url.pathname.endsWith('/live')||url.pathname.endsWith('/audience-layouts')){
  const id=crypto.randomUUID().replaceAll('-','');const score={schema:1,matchId:'audience-preview',sport:'pickleball',mode:'doubles',names:['André + Partner','Alexandra Montgomery-Smith + Pat'],points:['10','9'],sets:[1,0],setScores:[{a:11,b:5,matchTie:false},{a:10,b:9,matchTie:false}],bestOf:3,server:2,serveName:'Partner',scoreCall:'10–9–2',phase:'playing',winner:-1,rules:'Traditional side-out · to 11, win by two'};
  const cipher=await seal(id,'out',key,JSON.stringify(score));const r=await handleLive(new Request(url.origin+'/api/live',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id,ownerToken:owner,viewerToken:guest,cipher})}),env);if(!r.ok)return r;
  const audience=url.origin+'/live.html#id='+id+'&p='+guest+'&k='+key;
  if(url.pathname.endsWith('/audience-layouts'))return new Response('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Audience phone layout QA</title><style>body{margin:16px;background:#253148;color:white;font:14px system-ui;display:flex;gap:18px}iframe{border:1px solid #63738a;border-radius:14px;display:block}h2{font-size:14px;margin:0 0 8px}</style><section><h2>Portrait · 393 × 852 CSS px</h2><iframe title="Portrait audience" width="393" height="852" src="'+audience+'"></iframe></section><section><h2>Landscape · 852 × 393 CSS px</h2><iframe title="Landscape audience" width="852" height="393" src="'+audience+'"></iframe></section>',{headers:{'Content-Type':'text/html','Cache-Control':'no-store'}});
  return Response.redirect(audience,302);
 }
 if(url.pathname.endsWith('/result')){
  const id=url.searchParams.get('id');if(!/^[a-f0-9]{32}$/.test(id))return new Response('Invalid fixture',{status:400});
  const r=await handleTransfer(new Request(url.origin+'/api/transfer/'+id+'/incoming',{headers:{Authorization:'Bearer '+owner}}),env);const data=await r.json();
  return new Response(r.ok?await open(id,'in',key,data.cipher):JSON.stringify(data),{status:r.status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
 }
 const id=crypto.randomUUID().replaceAll('-','');const rows=[['1','preview-match','2026-11-04','1','martin','Martin','3','2026-11-04T09:00:00Z','2026-11-04T09:25:00Z','Europe/Vienna','1500','2','0','11','4','11','5','','','','','',''],['1','older-match','2026-11-03','1','','','3','','','','','0','2','4','11','5','11','','','','','','']];
 rows.push(['3','tennis-match','2026-11-08','1','martin','Martin','3','','','','','2','0','6','3','7','5','','','','','','','','tennis','singles','tennis,0,6,advantage,0','','','','','-1,-1,-1,-1;-1,-1,-1,-1','','','','']);
 const payload={myName:'André',supportsYourName:true,supportsFullBackup:true,supportsPickleball:true,fullBackup,supportsMultisport:true,supportsRetirement:true,selectedSport:'tennis',totalResults:3,schema:1,sessionId:id,baseDirectory:'synthetic-preview',currentMatchId:'current-preview',currentOpponentId:'',opponents:[{id:'martin',name:'Martin',archived:false,sports:['squash','tennis']}],csv:writeCsv(rows)};
 const cipher=await seal(id,'out',key,JSON.stringify(payload));const created=await handleTransfer(new Request(url.origin+'/api/transfer',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id,ownerToken:owner,guestToken:guest,cipher})}),env);
 if(!created.ok)return created;return Response.redirect(url.origin+'/transfer.html#id='+id+'&p='+guest+'&k='+key+'&view='+(['backup','opponents','results'].includes(url.searchParams.get('view'))?url.searchParams.get('view'):'results'),302);
}
