import {name,statistics} from './interchange.mjs?v=0.11.0';
export function initialView(value){return ['results','statistics','opponents','import'].includes(value)?value:'results';}
/** Catalog edits never select a match opponent. Match reassignment is a separate opt-in. */
export function addName(catalog,value){
 const n=name(value),found=catalog.find(e=>e.name.toLowerCase()===n.toLowerCase());
 if(found){found.archived=false;return found;}
 if(catalog.length>=1000)throw Error('Opponent storage is full');
 const entry={id:crypto.randomUUID(),name:n,archived:false};catalog.push(entry);return entry;
}
export function incomingPayload({snapshot,catalog,current,changeCurrent,csv,sessionId}){
 return {schema:1,sessionId,baseDirectory:snapshot.baseDirectory,currentMatchId:snapshot.currentMatchId,currentOpponentId:changeCurrent?current:(snapshot.currentOpponentId||''),changeCurrentOpponent:!!changeCurrent,opponents:catalog,csv};
}
export function checkRetirementSupport(snapshot,rows){if(!snapshot.supportsRetirement&&rows.some(r=>r[23]))throw Error('Update Squash Tap on your watch to v0.11.0 or newer before importing retired matches.');}
export function opponentStatistics(rows){const groups=new Map();for(const r of rows){const id=r[4]||'';if(!groups.has(id))groups.set(id,[]);groups.get(id).push(r);}return [...groups].map(([id,list])=>({id,fallback:list[0][5]||'Unnamed',...statistics(list)}));}
