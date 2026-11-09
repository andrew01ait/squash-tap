export const HEADER='schema,match_id,date,match_number,opponent_id,opponent_name,best_of,started_at,finished_at,time_zone,duration_seconds,me_sets,opponent_sets,set_1_me,set_1_opponent,set_2_me,set_2_opponent,set_3_me,set_3_opponent,set_4_me,set_4_opponent,set_5_me,set_5_opponent';
export const RETIREMENT_HEADER=HEADER+',retired_player';
export const MAX_PLAIN=4*1024*1024,MAX_CSV=3*1024*1024;
export const b64=bytes=>{let s='';for(let i=0;i<bytes.length;i+=8192)s+=String.fromCharCode(...bytes.subarray(i,i+8192));return btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');};
export const unb64=value=>Uint8Array.from(atob(value.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));
const bytes=value=>new TextEncoder().encode(value);
const context=(id,direction)=>{if(!/^[a-f0-9]{32}$/.test(id)||!['in','out'].includes(direction))throw Error('Invalid transfer context');return bytes('squashtap-transfer-v1|'+id+'|'+direction);};
async function streamBytes(stream,limit){const reader=stream.getReader(),chunks=[];let size=0;try{while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>limit)throw Error('Transfer is too large. Choose a smaller date range.');chunks.push(value);}}finally{await reader.cancel().catch(()=>{});}const out=new Uint8Array(size);let p=0;for(const c of chunks){out.set(c,p);p+=c.length;}return out;}
export async function seal(id,direction,key,text,providedIv){
 const plain=bytes(text);if(plain.length>MAX_PLAIN)throw Error('Transfer is too large');
 const compressed=await streamBytes(new Blob([plain]).stream().pipeThrough(new CompressionStream('gzip')),1024*1024);
 const raw=unb64(key);if(raw.length!==32)throw Error('Invalid key');const secret=await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['encrypt']);const iv=providedIv||crypto.getRandomValues(new Uint8Array(12));
 const cipher=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:context(id,direction)},secret,compressed);return '1.'+b64(iv)+'.'+b64(new Uint8Array(cipher));
}
export async function open(id,direction,key,envelope){
 if(typeof envelope!=='string'||envelope.length>1500000)throw Error('Invalid transfer');const p=envelope.split('.');if(p.length!==3||p[0]!=='1')throw Error('Unknown transfer format');const iv=unb64(p[1]),raw=unb64(key),cipher=unb64(p[2]);if(iv.length!==12||raw.length!==32||cipher.length>1024*1024+16)throw Error('Invalid transfer');
 const secret=await crypto.subtle.importKey('raw',raw,'AES-GCM',false,['decrypt']);const compressed=await crypto.subtle.decrypt({name:'AES-GCM',iv,additionalData:context(id,direction)},secret,cipher);
 const plain=await streamBytes(new Blob([compressed]).stream().pipeThrough(new DecompressionStream('gzip')),MAX_PLAIN);return new TextDecoder('utf-8',{fatal:true}).decode(plain);
}
const unsafe=c=>"=+-@\t\r\n'".includes(c);
const restore=s=>s.length>1&&s[0]==="'"&&unsafe(s[1])?s.slice(1):s;
const cell=s=>'"'+((s&&unsafe(s[0])?"'":'')+s).replace(/"/g,'""')+'"';
export function csvRows(text){
 if(typeof text!=='string'||text.length>MAX_CSV)throw Error('CSV is too large');text=text.replace(/^\ufeff/,'');const delim=/^"?schema"?;/.test(text)?';':',';const rows=[];let row=[],value='',quoted=false,closed=false;
 for(let i=0;i<text.length;i++){const c=text[i];if(quoted){if(c==='"'){if(text[i+1]==='"'){value+='"';i++;}else{quoted=false;closed=true;}}else value+=c;}
 else if(c==='"'){if(value||closed)throw Error('Unexpected CSV quote');quoted=true;}
 else if(c===delim||c==='\r'||c==='\n'){row.push(restore(value));value='';closed=false;if(c!==delim){if(c==='\r'&&text[i+1]==='\n')i++;if(row.length!==1||row[0]!=='')rows.push(row);row=[];if(rows.length>10001)throw Error('Too many CSV rows');}}
 else{if(closed)throw Error('Unexpected text after quoted CSV field');value+=c;}if(value.length>10000)throw Error('CSV field is too long');}
 if(quoted)throw Error('Unclosed CSV quote');if(value||closed||row.length){row.push(restore(value));rows.push(row);}return rows;
}
function integer(s){if(!/^\d+$/.test(s)||!Number.isSafeInteger(Number(s))||Number(s)>2147483647)throw Error('Invalid whole number');return Number(s);}
function fixedOffset(zone){if(['Z','UTC','UT','GMT'].includes(zone))return 0;const match=zone.match(/^(?:UTC|UT|GMT)?([+-])(\d{2}):(\d{2})(?::(\d{2}))?$/);if(!match)return null;const h=Number(match[2]),m=Number(match[3]),s=Number(match[4]||0);if(h>18||m>59||s>59||h===18&&(m||s))return null;return (match[1]==='-'?-1:1)*(h*3600+m*60+s)*1000;}
export function localDate(row){if(!row[7])return row[2];const offset=fixedOffset(row[9]);if(offset!==null)return new Date(Date.parse(row[7])+offset).toISOString().slice(0,10);const parts=new Intl.DateTimeFormat('en',{timeZone:row[9],year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(row[7]));return ['year','month','day'].map(type=>parts.find(p=>p.type===type).value).join('-');}
export function localMoment(row){if(!row[7])return row[2];const offset=fixedOffset(row[9]);return offset===null?new Date(row[7]).toLocaleString(undefined,{timeZone:row[9]}):new Date(Date.parse(row[7])+offset).toISOString().replace('T',' ').replace('Z','')+' '+row[9];}
export function name(value){if(/[\u0000-\u001f\u007f-\u009f]/.test(value))throw Error('Names cannot contain control characters');value=value.trim().normalize('NFC');if(!value||[...value].length>60||/[\uD800-\uDFFF]/u.test(value))throw Error('Use a name of 1–60 characters without control characters');return value;}
export function parseCsv(text){
 const rows=csvRows(text),retirements=rows.length&&rows[0].join(',')===RETIREMENT_HEADER;if(!rows.length||!retirements&&rows[0].join(',')!==HEADER)throw Error('Use a Squash Tap CSV export or download the empty template. Keep its column headings.');
 return rows.slice(1).map((c,index)=>{try{
  if(c.length!==(retirements?24:23)||c[0]!==(retirements?'2':'1')||!/^[-a-zA-Z0-9]{1,80}$/.test(c[1]))throw Error('Invalid schema, columns or ID');
  const retired=retirements?c[23]:'';if(!['','me','opponent'].includes(retired))throw Error('Invalid retired player');
  if(!/^\d{4}-\d{2}-\d{2}$/.test(c[2])||new Date(c[2]+'T00:00:00Z').toISOString().slice(0,10)!==c[2])throw Error('Invalid date');if(integer(c[3])<1)throw Error('Invalid match number');
  if(c[4]&&!/^[-a-zA-Z0-9]{1,80}$/.test(c[4]))throw Error('Invalid opponent ID');if(c[5])name(c[5]);else if(c[4])throw Error('Missing opponent name');const best=integer(c[6]);if(![3,5].includes(best))throw Error('Use best of 3 or 5');
  const scores=[];let ended=false,partial=false,me=0,op=0;for(let i=13;i<23;i+=2){if(!c[i]&&!c[i+1]){ended=true;continue;}if(ended||partial||me>=(best+1)/2||op>=(best+1)/2)throw Error('Invalid set progression');const a=integer(c[i]),b=integer(c[i+1]),high=Math.max(a,b),low=Math.min(a,b),done=low<10?high===11:high===low+2;if(a+b>10000||!done&&(!retired||high>=11&&high-low>=2))throw Error('Invalid final set score');scores.push([a,b]);if(done){if(a>b)me++;else op++;}else partial=true;}
  if(!scores.length||scores.length>best||(retired?Math.max(me,op)>=(best+1)/2:Math.max(me,op)!==(best+1)/2)||integer(c[11])!==me||integer(c[12])!==op)throw Error('Invalid match summary');
  const times=[c[7],c[8]].map(s=>{if(!s)return 0;if(!/^\d{4}-\d\d-\d\dT.*Z$/.test(s))throw Error('Use UTC ISO timestamps ending in Z');const n=Date.parse(s);if(!Number.isFinite(n)||n<0)throw Error('Invalid timestamp');return n;});
  if(times.some(Boolean)){try{new Intl.DateTimeFormat('en',{timeZone:c[9]}).format();}catch{if(fixedOffset(c[9])===null)throw Error('Invalid time zone');}}else if(c[9])throw Error('Time zone without timestamps');
  if(c[10]&&(times.some(t=>!t)||times[1]<times[0]||Number(c[10])!==Math.floor((times[1]-times[0])/1000)))throw Error('Duration does not match timestamps');return c;
 }catch(error){throw Error('CSV row '+(index+2)+': '+error.message);}});
}
export function writeCsv(rows){const retirements=rows.some(r=>r[23]);return '\ufeff'+(retirements?RETIREMENT_HEADER:HEADER)+'\r\n'+rows.map(r=>{const c=r.slice(0,23);c[0]=retirements?'2':'1';if(retirements)c.push(r[23]||'');return c.map(s=>cell(String(s))).join(',')+'\r\n';}).join('');}
export const rowWinner=row=>row[23]==='me'?1:row[23]==='opponent'?0:Number(row[11])>Number(row[12])?0:1;
export const rowOutcome=row=>(rowWinner(row)===0?'You won':'You lost')+(row[23]?' · '+(row[23]==='me'?'Me retired':'Opponent retired'):'');
export function statistics(rows){const s={played:0,won:0,lost:0,setsFor:0,setsAgainst:0,pointsFor:0,pointsAgainst:0};for(const r of rows){s.played++;s[rowWinner(r)===0?'won':'lost']++;s.setsFor+=Number(r[11]);s.setsAgainst+=Number(r[12]);for(let i=13;i<23;i+=2){s.pointsFor+=Number(r[i]||0);s.pointsAgainst+=Number(r[i+1]||0);}}return {...s,winRate:s.played?Math.round(100*s.won/s.played):null,balance:s.won-s.lost};}
