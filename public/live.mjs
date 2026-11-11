import {open} from './interchange.mjs?v=0.16.0';
import {sportName,sportMarkup} from './sports.mjs?v=0.16.0';
import {validateLive} from './live-data.mjs?v=0.16.0';
const $=id=>document.getElementById(id),params=new URLSearchParams(location.hash.slice(1));
const link={id:params.get('id'),token:params.get('p'),key:params.get('k')};let timer,controller,fetching=false,ended=false,last,updated=0;
function status(s,error=false){$('connection').textContent=s;$('connection').classList.toggle('error',error);}
function render(d){
 $('scoreboard').hidden=false;$('sport').replaceChildren();const mark=document.createElement('span');mark.innerHTML=sportMarkup(d.sport);$('sport').append(mark,document.createTextNode(' '+sportName(d.sport)+' · '+d.mode));$('phase').textContent=({ready:'Ready to play',playing:'Playing',paused:'Paused',interval:'Between games',finished:'Finished'})[d.phase];
 const nested=['tennis','padel'].includes(d.sport),final=d.phase==='finished';
 for(let i=0;i<2;i++){ $('name-'+i).textContent=d.names[i];$('score-'+i).textContent=final?d.sets[i]:d.points[i];const serves=!final&&d.server>=0&&(d.server>=4?d.server-4:d.server%2)===i;$('side-'+i).classList.toggle('serving',serves);$('serve-'+i).hidden=!serves;$('serve-'+i).textContent=serves?'Serving · '+d.serveName:'Serving';}
 $('score-unit').textContent=(final?(nested?'Final sets':'Final games'):(nested?'Points · sets ':'Points · games ')+d.sets.join('–'));$('score-call').textContent=!final&&d.scoreCall?'Serve call: '+d.scoreCall:'';$('rules').textContent=d.rules;
 $('set-scores').replaceChildren();d.setScores.forEach((s,i)=>{const e=document.createElement('span');e.textContent=(s.matchTie?'Match tie-break':nested?'Set':'Game')+' '+(i+1)+' · '+s.a+'–'+s.b;$('set-scores').append(e);});
 $('freshness').textContent='Last score update '+new Date(updated).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit',second:'2-digit'})+'. Updates arrive after a rally or match action.';
}
async function poll(){clearTimeout(timer);if(fetching||ended||document.hidden)return;fetching=true;controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),10000);
 try{const r=await fetch('/api/live/'+link.id,{cache:'no-store',credentials:'omit',redirect:'error',headers:{Authorization:'Bearer '+link.token},signal:controller.signal});if([401,403,404].includes(r.status)){ended=true;status('This audience link has ended or expired.',true);return;}if(!r.ok)throw Error('Live score service unavailable');const data=await r.json();if(!Number.isFinite(data.updatedAt)||!Number.isFinite(data.expiresAt)||Date.now()>=data.expiresAt)throw Error('Live link expired');const score=validateLive(JSON.parse(await open(link.id,'out',link.key,data.cipher)));last=score;updated=data.updatedAt;render(score);status('Connected · following this match');}
 catch(e){if(!document.hidden)status(last?'Connection interrupted · showing the last received score':'Could not connect · retrying shortly',true);}
 finally{clearTimeout(timeout);fetching=false;controller=null;if(!ended&&!document.hidden)timer=setTimeout(poll,last?.phase==='finished'?10000:3000);}
}
document.addEventListener('visibilitychange',()=>{clearTimeout(timer);if(document.hidden)controller?.abort();else poll();});window.addEventListener('online',poll);
$('palette').addEventListener('change',()=>{document.body.dataset.palette=$('palette').value;});
$('copy').addEventListener('click',async()=>{try{await navigator.clipboard.writeText(location.href);status('Audience link copied');}catch{status('Copy this page’s address to share the audience link.');}});
$('share').addEventListener('click',async()=>{try{if(navigator.share)await navigator.share({title:'Keep Score · live match',url:location.href});else $('copy').click();}catch(e){if(e.name!=='AbortError')status('Use Copy audience link to share.');}});
$('fullscreen').addEventListener('click',async()=>{try{if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();}catch{status('Fullscreen is unavailable in this browser.');}});
if(/^[a-f0-9]{32}$/.test(link.id)&&/^[-\w]{43}$/.test(link.token)&&/^[-\w]{43}$/.test(link.key)){status('Connecting to the live match…');poll();}
else if(link.id||link.token||link.key)status('Invalid audience link. Scan a fresh QR code from the scoring watch.',true);
