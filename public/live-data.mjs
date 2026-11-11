import {SPORTS} from './sports.mjs?v=0.16.0';
export function validateLive(d){
 const text=(s,max)=>typeof s==='string'&&[...s].length<=max&&!/[\u0000-\u001f\u007f-\u009f\uD800-\uDFFF]/u.test(s);
 const number=n=>Number.isInteger(n)&&n>=0&&n<=10000;
 if(!d||d.schema!==1||!SPORTS.includes(d.sport)||!['singles','doubles'].includes(d.mode)||!Array.isArray(d.names)||d.names.length!==2||!d.names.every(s=>text(s,260))||!Array.isArray(d.points)||d.points.length!==2||!d.points.every(s=>typeof s==='string'&&/^(?:AD|\d{1,5})$/.test(s))||!Array.isArray(d.sets)||d.sets.length!==2||!d.sets.every(n=>number(n)&&n<=4)||!Array.isArray(d.setScores)||d.setScores.length<1||d.setScores.length>7||!d.setScores.every(s=>number(s.a)&&number(s.b)&&typeof s.matchTie==='boolean')||!['ready','playing','paused','interval','finished'].includes(d.phase)||!Number.isInteger(d.server)||d.server< -1||d.server>5||!text(d.serveName,140)||!text(d.rules,300)||!text(d.scoreCall,60)||![-1,0,1].includes(d.winner))throw Error('Unsupported live score');
 return d;
}
