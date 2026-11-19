import {rowRules, scorePairs, details, finalScore} from './sports.mjs?v=0.16.3';
import {writeCsv, parseCsv} from './interchange.mjs?v=0.16.0';

const scoreColumns=[13,15,17,19,21,27,29];
export const bestOptions=sport=>sport==='pickleball'?[1,3,5]:sport==='table-tennis'?[3,5,7]:['padel','badminton'].includes(sport)?[3]:[3,5];
export function defaultRules(sport) {
  return {sport,nested:['tennis','padel'].includes(sport),doubles:['padel','pickleball'].includes(sport),target:{tennis:6,padel:6,badminton:21}[sport]??11,
    deuce:sport==='pickleball'?'side-out':sport==='padel'?'star':'advantage',finalTie:false};
}
export function todayLocal(now=new Date()) {
  return [now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),String(now.getDate()).padStart(2,'0')].join('-');
}
function invalid(message,index) { throw Object.assign(new Error(message),{scoreIndex:index}); }
const enteredPair=pair=>pair?.length===2&&pair.every(value=>/^\d{1,5}$/.test(String(value)));

export function entryWinners(rules,best,scores) {
  const winners=Array(best).fill(-1),wins=[0,0];
  for(let i=0;i<best;i++){
    if(!enteredPair(scores[i]))break;
    const [a,b]=scores[i].map(Number);
    if(!finalScore(rules,rules.finalTie&&i===best-1,a,b))break;
    const winner=a>b?0:1;
    winners[i]=winner;
    if(++wins[winner]>best/2)break;
  }
  return winners;
}

export function resultFromEntry({original=null,baseline=original,id,date,rules,best,players,scores,retired='',reference=1,today=todayLocal()}) {
  const row=Array(36).fill('');
  row[0]='3';row[1]=original?.[1]??id;row[2]=date;row[3]=original?.[3]??String(reference);
  row[6]=String(best);row[23]=retired;row[24]=rules.sport;row[25]=rules.doubles?'doubles':'singles';
  row[26]=[rules.sport,+rules.doubles,rules.target,rules.deuce,+rules.finalTie].join(',');
  const f=rowRules(row),needed=Math.floor(best/2)+1;
  const fixedDate=!!(original?.[7]||original?.[8]);
  if (original) {
    const previous=rowRules(original);
    if (f.sport!==previous.sport||row[26]!==[previous.sport,+previous.doubles,previous.target,previous.deuce,+previous.finalTie].join(',')||best!==Number(original[6]))
      invalid('Keep the recorded format.');
    for(let i=7;i<=10;i++) row[i]=original[i];
    if (fixedDate) row[2]=original[2];
  }
  if (!fixedDate&&(!/^\d{4}-\d{2}-\d{2}$/.test(date)||!Number.isFinite(Date.parse(date))||new Date(date+'T00:00:00Z').toISOString().slice(0,10)!==date||
      date!==original?.[2]&&(date<'1900-01-01'||date>today))) invalid('Choose today or an earlier match date.');
  const ids=new Set();
  for(const [slot,index] of [4,32,34].entries()) {
    const person=players[slot];
    if(!person||slot>0&&!f.doubles)continue;
    if(ids.has(person.id))invalid('Choose a different player for each position.');
    ids.add(person.id);row[index]=person.id;row[index+1]=person.name;
  }
  const previousScores=baseline?scorePairs(baseline):[], previousDetails=baseline?details(baseline):[];
  const detail=[],wins=[0,0];
  let ended=false,partial=false;
  for(const [i,pair] of scores.entries()) {
    if(!pair||pair.every(s=>s==='')){ended=true;continue;}
    if(ended||partial||Math.max(...wins)>=needed||i>=best)invalid('Remove scores after the last game.',i);
    if(!enteredPair(pair))invalid('Enter both scores.',i);
    const [a,b]=pair.map(Number),tie=f.finalTie&&i===best-1,done=finalScore(f,tie,a,b);
    const high=Math.max(a,b),cap=f.sport==='badminton'?(f.target===21?30:21):Infinity;
    const possiblePartial=f.nested&&!tie?a<=6&&b<=6:high<(tie?Infinity:cap)&&(high<(tie?10:f.target)||Math.abs(a-b)<2);
    if(a+b>10000||!done&&(!retired||!possiblePartial))invalid('Check the score against the selected rules.',i);
    const same=previousScores[i]?.[0]===a&&previousScores[i]?.[1]===b;
    if(done)wins[a>b?0:1]++;else partial=true;
    row[scoreColumns[i]]=String(a);row[scoreColumns[i]+1]=String(b);
    detail.push(same?previousDetails[i].join(','):[f.nested?-1:a,f.nested?-1:b,-1,-1,-1,-1].join(','));
  }
  if(!detail.length||(!retired&&Math.max(...wins)!==needed))invalid('Enter enough games to decide the match.');
  if(retired&&Math.max(...wins)>=needed)invalid('A decided match cannot end by retirement.');
  row[11]=String(wins[0]);row[12]=String(wins[1]);row[31]=detail.join(';');
  if(f.sport==='squash'&&original?.[0]!=='3'){row[0]=retired?'2':'1';row.length=retired?24:23;}
  parseCsv(writeCsv([row]));
  return row;
}
export function entryChanges(pending,originals,imported) {
  const before=[],after=[],added=[];
  for(const [id,row] of pending) {
    if(originals.has(id)){before.push(originals.get(id));after.push(row);}
    else added.push(row);
  }
  const csv=added.length?writeCsv([...imported,...added]):'';
  return {csv,edits:before.length?{before:writeCsv(before),after:writeCsv(after)}:null};
}
