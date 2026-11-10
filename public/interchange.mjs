import {rowRules,scorePairs,finalScore,details} from './sports.mjs?v=0.14.0';
export const HEADER='schema,match_id,date,match_number,opponent_id,opponent_name,best_of,started_at,finished_at,time_zone,duration_seconds,me_sets,opponent_sets,set_1_me,set_1_opponent,set_2_me,set_2_opponent,set_3_me,set_3_opponent,set_4_me,set_4_opponent,set_5_me,set_5_opponent';
export const RETIREMENT_HEADER=HEADER+',retired_player';
export const MULTISPORT_HEADER=RETIREMENT_HEADER+',sport,mode,rules,set_6_me,set_6_opponent,set_7_me,set_7_opponent,score_details,partner_id,partner_name,opponent_2_id,opponent_2_name';
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
 const rows=csvRows(text),multi=rows.length&&rows[0].join(',')===MULTISPORT_HEADER,retirements=multi||rows.length&&rows[0].join(',')===RETIREMENT_HEADER;
 if(!rows.length||!retirements&&rows[0].join(',')!==HEADER)throw Error('Use a Squash Tap CSV export with its original headings.');
 return rows.slice(1).map((c,index)=>{try{
  if(c.length!==(multi?36:retirements?24:23)||c[0]!==(multi?'3':retirements?'2':'1')||!/^[-a-zA-Z0-9]{1,80}$/.test(c[1]))throw Error('Invalid schema, columns or ID');
  const retired=retirements?c[23]:'';if(!['','me','opponent'].includes(retired))throw Error('Invalid retired side');
  if(!/^\d{4}-\d{2}-\d{2}$/.test(c[2])||new Date(c[2]+'T00:00:00Z').toISOString().slice(0,10)!==c[2]||integer(c[3])<1)throw Error('Invalid date or reference');
  const f=rowRules(c),best=integer(c[6]),options=f.sport==='table-tennis'?[3,5,7]:['badminton','padel'].includes(f.sport)?[3]:[3,5];if(!options.includes(best))throw Error('Invalid match length');
  for(const i of multi?[4,32,34]:[4]){if(c[i]&&!/^[-a-zA-Z0-9]{1,80}$/.test(c[i]))throw Error('Invalid player ID');if(c[i+1])name(c[i+1]);else if(c[i])throw Error('Missing player name');}
  if(multi&&!f.doubles&&(c[33]||c[35]))throw Error('Extra players in singles');const ids=(multi?[c[4],c[32],c[34]]:[c[4]]).filter(Boolean);if(new Set(ids).size!==ids.length)throw Error('Duplicate player');
  const raw=multi?[13,15,17,19,21,27,29]:[13,15,17,19,21];let ended=false,partial=false,me=0,op=0;const scores=[];
  for(const i of raw){if(!c[i]&&!c[i+1]){ended=true;continue;}if(ended||partial||Math.max(me,op)>=(best+1)/2)throw Error('Invalid score progression');const a=integer(c[i]),b=integer(c[i+1]),tie=f.finalTie&&scores.length===best-1,done=finalScore(f,tie,a,b),hi=Math.max(a,b),cap=f.sport==='badminton'?(f.target===21?30:21):Infinity;const validPartial=f.nested&&!tie?a<=6&&b<=6&&!done:hi<(tie?Infinity:cap)&&(hi<(tie?10:f.target)||Math.abs(a-b)<2);if(a+b>10000||!done&&(!retired||!validPartial))throw Error('Invalid final set score');scores.push([a,b]);if(done){if(a>b)me++;else op++;}else partial=true;}
  if(!scores.length||scores.length>best||(retired?Math.max(me,op)>=(best+1)/2:Math.max(me,op)!==(best+1)/2)||integer(c[11])!==me||integer(c[12])!==op)throw Error('Invalid match summary');
  if(multi){if(!/^-?\d+,-?\d+,-?\d+,-?\d+(?:,-?\d+,-?\d+)?(?:;-?\d+,-?\d+,-?\d+,-?\d+(?:,-?\d+,-?\d+)?)*$/.test(c[31]))throw Error('Invalid point details');const ds=details(c);if(ds.length!==scores.length)throw Error('Invalid point details');for(let i=0;i<ds.length;i++){const d=ds[i];if(![4,6].includes(d.length)||d.length===6&&((d[4]<0)!==(d[5]<0)||d[4]>=0&&(!f.nested||f.finalTie&&i===best-1||finalScore(f,false,...scores[i])))||d.some(n=>!Number.isInteger(n)||n< -1||n>10000)||(d[0]<0)!==(d[1]<0)||(d[2]<0)!==(d[3]<0)||d[0]+d[1]>10000)throw Error('Invalid point totals');if(d[2]>=0&&(!f.nested||f.finalTie&&i===best-1||Math.max(...scores[i])!==7||Math.min(...scores[i])!==6||Math.max(d[2],d[3])<7||Math.abs(d[2]-d[3])<2||(d[2]>d[3])!==(scores[i][0]>scores[i][1])))throw Error('Invalid tie-break details');}}
  const times=[c[7],c[8]].map(s=>{if(!s)return 0;if(!/^\d{4}-\d\d-\d\dT.*Z$/.test(s))throw Error('Use UTC ISO timestamps');const n=Date.parse(s);if(!Number.isFinite(n)||n<0)throw Error('Invalid timestamp');return n;});
  if(times.some(Boolean)){try{new Intl.DateTimeFormat('en',{timeZone:c[9]}).format();}catch{if(fixedOffset(c[9])===null)throw Error('Invalid time zone');}}else if(c[9])throw Error('Time zone without timestamps');
  if(c[10]&&(times.some(t=>!t)||times[1]<times[0]||Number(c[10])!==Math.floor((times[1]-times[0])/1000)))throw Error('Duration does not match timestamps');return c;
 }catch(error){throw Error('CSV row '+(index+2)+': '+error.message);}});
}
export function writeCsv(rows){const multi=rows.some(r=>r[0]==='3'),retirements=multi||rows.some(r=>r[23]);return '\ufeff'+(multi?MULTISPORT_HEADER:retirements?RETIREMENT_HEADER:HEADER)+'\r\n'+rows.map(r=>{let c=[...r];if(multi){if(c[0]!=='3'){c=c.slice(0,23);c.push(r[23]||'','squash','singles','squash,0,11,advantage,0','','','','',scorePairs(r).filter(Boolean).map(([a,b])=>[a,b,-1,-1].join(',')).join(';'),'','','','');}c[0]='3';}else{c=c.slice(0,23);c[0]=retirements?'2':'1';if(retirements)c.push(r[23]||'');}return c.map(s=>cell(String(s))).join(',')+'\r\n';}).join('');}
export const rowWinner=row=>row[23]==='me'?1:row[23]==='opponent'?0:Number(row[11])>Number(row[12])?0:1;
export const rowOutcome=row=>(rowWinner(row)===0?'You won':'You lost')+(row[23]?' · '+(row[23]==='me'?'Me retired':'Opponent retired'):'');
export function statistics(rows){const s={played:0,won:0,lost:0,setsFor:0,setsAgainst:0,pointsFor:0,pointsAgainst:0,gamesFor:0,gamesAgainst:0,unknownPointMatches:0};for(const r of rows){s.played++;s[rowWinner(r)===0?'won':'lost']++;s.setsFor+=Number(r[11]);s.setsAgainst+=Number(r[12]);const f=rowRules(r),ds=details(r),scores=scorePairs(r).filter(Boolean);let missing=false;for(let i=0;i<ds.length;i++){const d=ds[i];if(d[0]>=0){s.pointsFor+=d[0];s.pointsAgainst+=d[1];}else missing=true;if(f.nested&&!(f.finalTie&&i===Number(r[6])-1)){s.gamesFor+=scores[i][0];s.gamesAgainst+=scores[i][1];}}if(missing)s.unknownPointMatches++;}return {...s,winRate:s.played?Math.round(100*s.won/s.played):null,balance:s.won-s.lost};}
