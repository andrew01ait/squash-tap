import {participants} from './sports.mjs?v=0.16.0';
import {name,statistics} from './interchange.mjs?v=0.16.0';
export function initialView(value){return ['results','statistics','opponents','import','backup'].includes(value)?value:'results';}
/** Catalog edits never select a match opponent. Match reassignment is a separate opt-in. */
export function addName(catalog,value){
 const n=name(value),found=catalog.find(e=>e.name.toLowerCase()===n.toLowerCase());
 if(found){found.archived=false;return found;}
 if(catalog.length>=1000)throw Error('Opponent storage is full');
 const entry={id:crypto.randomUUID(),name:n,archived:false};catalog.push(entry);return entry;
}
export function incomingPayload({snapshot,catalog,current,changeCurrent,csv,sessionId,myName}){
 const out={schema:1,sessionId,baseDirectory:snapshot.baseDirectory,currentMatchId:snapshot.currentMatchId,currentOpponentId:changeCurrent?current:(snapshot.currentOpponentId||''),changeCurrentOpponent:!!changeCurrent,opponents:catalog,csv};if(snapshot.supportsYourName&&typeof myName==='string'&&myName!==(snapshot.myName||'')){out.changeMyName=true;out.myName=myName;out.baseMyName=snapshot.myName||'';}return out;
}
export function checkRetirementSupport(snapshot,rows){if(!snapshot.supportsRetirement&&rows.some(r=>r[23]))throw Error('Update Keep Score on your watch to v0.11.0 or newer before importing retired matches.');}
export function opponentStatistics(rows){const groups=new Map();for(const r of rows){const ids=[r[4]||'',...(r[0]==='3'&&r[34]?[r[34]]:[])];for(const id of ids){if(!groups.has(id))groups.set(id,[]);groups.get(id).push(r);}}return [...groups].map(([id,list])=>({id,fallback:list[0][5]||'Unnamed',...statistics(list)}));}
