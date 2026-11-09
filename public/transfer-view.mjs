import {localDate,localMoment,rowWinner,rowOutcome,statistics} from './interchange.mjs?v=0.11.0';
import {opponentStatistics} from './transfer-state.mjs?v=0.11.0';
const $=id=>document.getElementById(id);
export const newest=rows=>[...rows].sort((a,b)=>(b[7]||b[2]).localeCompare(a[7]||a[2])||Number(b[3])-Number(a[3]));
const line=(parent,text,tag='p')=>{const e=document.createElement(tag);e.textContent=text;parent.append(e);return e;};
const balance=n=>n>0?'+'+n:String(n);
export function shortRecord(s){return s.played+' matches · '+s.won+' won / '+s.lost+' lost'+(s.winRate===null?'':' · '+s.winRate+'% wins');}
function moment(row,index){if(!row[index])return 'Not recorded';const copy=[...row];copy[7]=row[index];return localMoment(copy);}
export function resultCard(row,opponent){
 const card=document.createElement('details');card.className='result'+(rowWinner(row)===1?' lost':'');card.dataset.matchId=row[1];
 const summary=document.createElement('summary');line(summary,localMoment(row)+' · vs '+opponent);line(summary,'Sets · Me '+row[11]+'–'+row[12]+' '+opponent,'strong');line(summary,rowOutcome(row),'span');card.append(summary);
 const detail=document.createElement('div');detail.className='result-detail';line(detail,'Best of '+row[6]+' · Match '+row[3]);line(detail,'Start: '+moment(row,7)+'\nEnd: '+moment(row,8));line(detail,'Duration: '+(row[10]?Math.floor(Number(row[10])/60)+' min '+Number(row[10])%60+' sec':'Not recorded'));
 const sets=[];for(let i=13;i<23;i+=2)if(row[i]!==''){const a=Number(row[i]),b=Number(row[i+1]),hi=Math.max(a,b),lo=Math.min(a,b),done=lo<10?hi===11:hi===lo+2;sets.push('Set '+((i-13)/2+1)+': '+a+'–'+b+(done?'':' · unfinished'));}line(detail,sets.join('\n'));card.append(detail);return card;
}
function metric(parent,title,value,detail){const c=document.createElement('article');c.className='metric';line(c,title,'h3');line(c,String(value),'strong');line(c,detail);parent.append(c);}
export function renderStatistics(rows,catalog,totalResults,onOpponent,onMatch){
 const overall=statistics(rows),summary=$('overall-summary');summary.replaceChildren();
 $('statistics-scope').textContent=(Number.isInteger(totalResults)&&totalResults===rows.length?'All saved matches':'All transferred matches')+' · across all players. Browsing filters do not apply.'+(Number.isInteger(totalResults)&&totalResults!==rows.length?' This transfer includes '+rows.length+' of '+totalResults+' saved results. Use Browse on phone on the watch for your complete history.':'');
 metric(summary,'Matches',overall.played,overall.won+' won / '+overall.lost+' lost\nWin rate: '+(overall.winRate===null?'—':overall.winRate+'%')+'\nBalance: '+balance(overall.balance));
 metric(summary,'Sets (games)',overall.setsFor+overall.setsAgainst,overall.setsFor+' won / '+overall.setsAgainst+' lost');
 metric(summary,'Points',overall.pointsFor+overall.pointsAgainst,overall.pointsFor+' for / '+overall.pointsAgainst+' against');
 const form=$('recent-form');form.replaceChildren();const recent=newest(rows).slice(0,12).reverse();
 for(const r of recent){const b=document.createElement('button');b.textContent=rowWinner(r)===0?'W':'L';if(rowWinner(r)===1)b.className='lost';b.setAttribute('aria-label',rowOutcome(r)+' against '+(catalog.find(e=>e.id===r[4])?.name||r[5]||'Unnamed')+' · '+localMoment(r));b.title=b.getAttribute('aria-label');b.addEventListener('click',()=>onMatch(r[1]));form.append(b);}if(!rows.length)line(form,'No saved matches in this transfer yet.');
 const list=$('opponent-balances');list.replaceChildren();const groups=opponentStatistics(rows).sort((a,b)=>b.played-a.played||a.fallback.localeCompare(b.fallback));
 for(const s of groups){const card=document.createElement('article');card.className='opponent-balance';const b=document.createElement('button');b.className='person-link';b.textContent=s.id?(catalog.find(e=>e.id===s.id)?.name||s.fallback):'Unnamed';b.addEventListener('click',()=>onOpponent(s.id));card.append(b);line(card,shortRecord(s)+'\nMatch balance: '+balance(s.balance)+'\nSets: '+s.setsFor+'–'+s.setsAgainst+' · Points: '+s.pointsFor+'–'+s.pointsAgainst);list.append(card);}
}
