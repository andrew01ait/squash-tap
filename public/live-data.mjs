import {SPORTS} from './sports.mjs?v=0.16.3';
export function validateLive(d){
 if(d?.schema===1&&d.phase==='discarded'){if(Object.keys(d).some(k=>!['schema','phase'].includes(k)))throw Error('Discard notice must not contain match data');return d;}
 const text=(s,max)=>typeof s==='string'&&[...s].length<=max&&!/[\u0000-\u001f\u007f-\u009f\uD800-\uDFFF]/u.test(s);
 const number=n=>Number.isInteger(n)&&n>=0&&n<=10000;
 if(!d||d.schema!==1||!SPORTS.includes(d.sport)||!['singles','doubles'].includes(d.mode)||!Array.isArray(d.names)||d.names.length!==2||!d.names.every(s=>text(s,260))||!Array.isArray(d.points)||d.points.length!==2||!d.points.every(s=>typeof s==='string'&&/^(?:AD|\d{1,5})$/.test(s))||!Array.isArray(d.sets)||d.sets.length!==2||!d.sets.every(n=>number(n)&&n<=4)||!Array.isArray(d.setScores)||d.setScores.length<1||d.setScores.length>7||!d.setScores.every(s=>number(s.a)&&number(s.b)&&typeof s.matchTie==='boolean')||!['ready','playing','paused','interval','finished'].includes(d.phase)||!Number.isInteger(d.server)||d.server< -1||d.server>5||!text(d.serveName,140)||!text(d.rules,300)||!text(d.scoreCall,60)||![-1,0,1].includes(d.winner))throw Error('Unsupported live score');
 const pair=s=>Array.isArray(s)&&s.length===2&&s.every(n=>number(n)&&n<=4);
 const scores=s=>Array.isArray(s)&&s.length>=1&&s.length<=7&&s.every(g=>number(g.a)&&number(g.b)&&typeof g.matchTie==='boolean'&&typeof g.complete==='boolean');
 const retired=n=>[-1,0,1].includes(n);
 if(d.retiredPlayer!==undefined&&!retired(d.retiredPlayer)||d.finishedAt!==undefined&&(!Number.isSafeInteger(d.finishedAt)||d.finishedAt<0))throw Error('Unsupported final result');
 if(d.headToHead!==undefined){const h=d.headToHead,l=h?.last;
  if(!h||!number(h.won)||!number(h.lost)||h.won+h.lost<1||h.won+h.lost>10000||!l||!/^\d{4}-\d{2}-\d{2}$/.test(l.date)||Number.isNaN(Date.parse(l.date+'T00:00:00Z'))||new Date(l.date+'T00:00:00Z').toISOString().slice(0,10)!==l.date||!pair(l.sets)||!scores(l.scores)||![0,1].includes(l.winner)||!retired(l.retiredPlayer))throw Error('Unsupported previous-meeting summary');
 }
 if(d.phase==='finished'&&![0,1].includes(d.winner))throw Error('Final result needs a winner');
 return d;
}
