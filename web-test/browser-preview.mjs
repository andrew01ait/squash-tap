/** Development-only synthetic watch. This module is removed from production builds. */
import {handleTransfer} from '../worker/relay.mjs';
import {seal,open,b64,writeCsv} from '../public/interchange.mjs';
const owner='w'.repeat(43),guest='p'.repeat(43),key=b64(new Uint8Array(32).fill(7));
export async function preview(request,env){
 const url=new URL(request.url);
 if(url.pathname.endsWith('/result')){
  const id=url.searchParams.get('id');if(!/^[a-f0-9]{32}$/.test(id))return new Response('Invalid fixture',{status:400});
  const r=await handleTransfer(new Request(url.origin+'/api/transfer/'+id+'/incoming',{headers:{Authorization:'Bearer '+owner}}),env);const data=await r.json();
  return new Response(r.ok?await open(id,'in',key,data.cipher):JSON.stringify(data),{status:r.status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
 }
 const id=crypto.randomUUID().replaceAll('-','');const rows=[['1','preview-match','2026-11-04','1','martin','Martin','3','2026-11-04T09:00:00Z','2026-11-04T09:25:00Z','Europe/Vienna','1500','2','0','11','4','11','5','','','','','',''],['1','older-match','2026-11-03','1','','','3','','','','','0','2','4','11','5','11','','','','','','']];
 rows.push(['3','tennis-match','2026-11-08','1','martin','Martin','3','','','','','2','0','6','3','7','5','','','','','','','','tennis','singles','tennis,0,6,advantage,0','','','','','-1,-1,-1,-1;-1,-1,-1,-1','','','','']);
 const payload={supportsMultisport:true,supportsRetirement:true,selectedSport:'tennis',totalResults:3,schema:1,sessionId:id,baseDirectory:'synthetic-preview',currentMatchId:'current-preview',currentOpponentId:'',opponents:[{id:'martin',name:'Martin',archived:false,sports:['squash','tennis']}],csv:writeCsv(rows)};
 const cipher=await seal(id,'out',key,JSON.stringify(payload));const created=await handleTransfer(new Request(url.origin+'/api/transfer',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id,ownerToken:owner,guestToken:guest,cipher})}),env);
 if(!created.ok)return created;return Response.redirect(url.origin+'/transfer.html#id='+id+'&p='+guest+'&k='+key,302);
}
