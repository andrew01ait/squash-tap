import {downloadResultCard,shareResultCard} from './result-card.mjs?v=3';
import {scoreValue} from './match-sheet.mjs?v=2';
import {rowSport,rowMode,rowRules,scorePairs,finalScore,details,SPORTS,sportMarkup} from './sports.mjs?v=0.16.3';
import {rowWinner,statistics} from './interchange.mjs?v=0.16.0';
import {byPlayer,matchInsights,matchesPerson,newest} from './transfer-state.mjs?v=7';
import {tx,modeText,sportText,durationText,errorText,momentText,outcomeText} from './transfer-copy.mjs?v=10';
import {language} from './locale.mjs';
export {newest};
const $=id=>document.getElementById(id);
export function node(tag,text,className) {
  const el=document.createElement(tag);
  if(text!==undefined) el.textContent=text;
  if(className) el.className=className;
  return el;
}
const record=s=>tx('{0} won · {1} lost',s.won,s.lost);
export const shortRecord=s=>[tx('{0} matches',s.played),record(s),s.winRate===null?'':s.winRate+'%'].filter(Boolean).join(' · ');
export function orderedSports(ids=SPORTS) {
  return [...ids].sort((a,b)=>sportText(a).localeCompare(sportText(b),language()));
}
function sportMark(sport) {
  const el=node('span',undefined,'sport-mark');el.dataset.sport=sport;el.innerHTML=sportMarkup(sport);el.setAttribute('aria-hidden','true');return el;
}
function statRow(parent,label,value) {
  const el=node('div');el.append(node('dt',label),node('dd',value));parent.append(el);
}
export function pointSummary(rows,totals) {
  const hasPoints=rows.some(row=>details(row).some(([points])=>points>=0));
  if(!hasPoints) return {value:'—',note:tx('Not recorded')};
  const value=totals.pointsFor+' – '+totals.pointsAgainst;
  const note=totals.unknownPointMatches?tx('Unknown points omitted in {0} matches.',totals.unknownPointMatches):'';
  return {value,note};
}
function playerLine(name,score,side,won) {
  const line=node('span',undefined,'competitor '+side);
  const total=node('b');
  total.append(scoreValue(score,won));
  line.append(node('span',name),total);return line;
}
function tennisPoints(a,b) { return a>=3&&b>=3?(a>b?'AD':'40'):['0','15','30','40'][Math.min(a,3)]; }
export function resultCard(row,opponent,myName=tx('Me')) {
  const rules=rowRules(row),gameDetails=details(row),pairs=scorePairs(row).filter(Boolean);
  const mine=rules.doubles?myName+' + '+(row[33]||tx('Partner')):myName;
  const other=rules.doubles?opponent+' + '+(row[35]||tx('Opponent 2')):opponent;
  const won=rowWinner(row)===0;
  const card=node('details',undefined,'result');card.dataset.matchId=row[1];
  if(!won) card.classList.add('lost');
  const summary=node('summary',undefined,'result-summary');
  const meta=node('span',undefined,'match-meta'),date=node('time',momentText(row));
  date.dateTime=row[7]||row[2];
  meta.append(node('span',sportText(rules.sport)+' · '+modeText(rowMode(row)),'match-sport'),date);
  const teams=node('span',undefined,'match-teams');
  teams.append(playerLine(mine,row[11],'mine',won),playerLine(other,row[12],'other',!won));
  summary.append(meta,teams,node('span',outcomeText(row),'outcome'));
  card.append(summary);
  const detail=node('div',undefined,'result-detail');
  detail.append(node('p',tx('Best of {0}',row[6])+' · '+tx('Match {0}',row[3]),'muted'));
  const sheet=node('table',undefined,'score-sheet');
  const head=sheet.createTHead().insertRow(),body=sheet.createTBody();
  for(const title of [tx(rules.nested?'Set':'Game'),mine,other]){
    const cell=node('th',title);cell.scope='col';head.append(cell);
  }
  pairs.forEach(([a,b],i)=>{
    const tie=rules.finalTie&&i===Number(row[6])-1;
    const complete=finalScore(rules,tie,a,b),line=body.insertRow();
    const period=node('th',tie?tx('Match tie-break'):String(i+1));period.scope='row';line.append(period);
    for(const [value,opposing] of [[a,b],[b,a]]){
      const cell=line.insertCell();
      cell.append(scoreValue(value,complete&&value>opposing));
    }
    const more=[];
    if(gameDetails[i][2]>=0) more.push('TB '+gameDetails[i][2]+' – '+gameDetails[i][3]);
    if(!complete) {
      more.push(tx('Unfinished'));
      if(gameDetails[i][4]>=0) {
        const x=gameDetails[i][4],y=gameDetails[i][5];
        const score=a===6&&b===6?x+' – '+y:tennisPoints(x,y)+' – '+tennisPoints(y,x);
        more.push(tx('Points')+' '+score);
      }
    }
    if(more.length) {
      const notes=body.insertRow();notes.className='score-note';
      const cell=notes.insertCell();cell.colSpan=3;cell.textContent=more.join(' · ');
    }
  });
  if(pairs.length) detail.append(sheet);
  const times=node('dl',undefined,'timing');
  statRow(times,tx('Start'),momentText(row,7,'required'));statRow(times,tx('End'),momentText(row,8,'required'));
  statRow(times,tx('Duration'),row[10]?durationText(Number(row[10])):tx('Not recorded'));
  detail.append(times);
  const actions=node('div',undefined,'result-actions');
  const sharing=node('div',undefined,'actions result-sharing');
  for(const [title,action] of [['Download result card',downloadResultCard],['Share result card',shareResultCard]]) {
    const b=node('button',tx(title),'secondary');
    b.addEventListener('click',async()=>{b.disabled=true;try{await action(row,opponent,myName);}catch(e){if(e.name!=='AbortError')alert(errorText('image'));}finally{b.disabled=false;}});
    sharing.append(b);
  }
  actions.append(sharing);detail.append(actions);card.append(detail);return card;
}
function metric(parent,title,value,note) {
  const article=node('article',undefined,'metric');
  article.append(node('h4',title),node('strong',value));if(note)article.append(node('p',note));parent.append(article);
}
function rateValue(rate) {
  const value=node('span',undefined,'record-value');
  value.append(node('b',rate===null?'—':rate+'%'));
  if(rate!==null){
    const bar=node('progress');bar.max=100;bar.value=rate;bar.setAttribute('aria-hidden','true');value.append(bar);
  }
  return value;
}
function recordButton(parent,title,sub,rate,action,icon=null) {
  const b=node('button',undefined,'record-row');
  const label=node('span',undefined,'record-label');label.append(node('strong',title),node('small',sub));
  if(icon) b.append(icon);
  b.append(label,rateValue(rate));b.addEventListener('click',action);parent.append(b);
}
function renderPlayers(rows,catalog,role,onPlayer,selectedSport) {
  const list=$(role==='partner'?'partner-balances':'opponent-balances');
  list.replaceChildren();
  const display=entry=>catalog.get(entry.id)?.name||entry.fallback||tx('Unnamed');
  const groups=byPlayer(rows,role).sort((a,b)=>b.played-a.played||display(a).localeCompare(display(b),language()));
  for(const g of groups) {
    const acrossSports=selectedSport==='all';
    const score=acrossSports?'':tx(['tennis','padel'].includes(selectedSport)?'Sets':'Games')+' '+g.setsFor+'–'+g.setsAgainst;
    const disclosure=node('details',undefined,'player-record'),summary=node('summary',undefined,'record-row');
    const label=node('span',undefined,'record-label');
    label.append(node('strong',display(g)),node('small',[record(g),score].filter(Boolean).join(' · ')));
    summary.append(label,rateValue(g.winRate));
    disclosure.append(summary);list.append(disclosure);
    disclosure.addEventListener('toggle',()=>{
      if(!disclosure.open||disclosure.children.length>1) return;
      const meetings=rows.filter(r=>matchesPerson(r,g.id,role));
      const last=newest(meetings)[0],insights=matchInsights(meetings);
      const detail=node('div',undefined,'player-detail'),table=node('dl',undefined,'timing');
      statRow(table,tx('Last meeting'),momentText(last)+' · '+outcomeText(last)+' · '+last[11]+'–'+last[12]);
      if(acrossSports) {
        for(const sport of orderedSports(new Set(meetings.map(rowSport)))) statRow(table,sportText(sport),record(statistics(meetings.filter(r=>rowSport(r)===sport))));
      } else {
        const points=pointSummary(meetings,g);
        statRow(table,tx('Points'),[points.value,points.note].filter(Boolean).join(' · '));
      }
      statRow(table,tx('Current streak'),insights.current+' · '+tx(insights.last===0?'Consecutive wins':'Consecutive losses'));
      if(insights.timed)statRow(table,tx('Average duration'),durationText(insights.averageSeconds)+' · '+tx('{0} timed matches',insights.timed));
      const browse=node('button',tx('See match history'),'secondary');browse.addEventListener('click',()=>onPlayer(g.id,role));
      detail.append(table,browse);disclosure.append(detail);
    });
  }
  if(!groups.length) list.append(node('p',tx(role==='partner'?'No named partners in this selection.':'No matches in this selection.'),'muted'));
}
export function renderSquashTime({timed,totalSeconds},matchCount) {
  $('squash-time').textContent=totalSeconds===null?tx('Not recorded'):durationText(totalSeconds);
  $('squash-time-coverage').textContent=tx('{0} of {1} squash matches in this journal have recorded times.',timed,matchCount);
}

const MONTHS_PER_PAGE=12;
const EMPTY_MONTH={played:0,won:0,lost:0,winRate:null,totalSeconds:null,averageSeconds:null};
export function renderMonthChart({records,first,last},page) {
  const chart=$('month-chart'),metric=$('month-metric').value;
  const isDuration=metric==='totalSeconds'||metric==='averageSeconds';
  const empty=!records.size;
  chart.replaceChildren();
  chart.hidden=empty;
  $('month-chart-empty').hidden=!empty;
  $('month-metric-field').hidden=empty;
  $('month-chart-time-note').hidden=empty||!isDuration;
  const pages=empty?0:Math.ceil((last-first+1)/MONTHS_PER_PAGE);
  $('month-pages').hidden=pages<=1;
  if(empty) return 0;

  page=Math.min(pages-1,Math.max(0,page));
  const end=last-page*MONTHS_PER_PAGE;
  const start=Math.max(first,end-MONTHS_PER_PAGE+1);
  const max=metric==='winRate'?100:Math.max(1,...Array.from(records.values(),record=>record[metric]??0));
  const dateFormat=new Intl.DateTimeFormat(language(),{month:'short',year:'numeric',timeZone:'UTC'});
  const numberFormat=new Intl.NumberFormat(language(),metric==='winRate'?{style:'percent'}:{});
  for(let month=start;month<=end;month++) {
    const date=new Date(0);date.setUTCFullYear(Math.floor(month/12),month%12,1);
    const row=node('div'),values=node('dd');
    const value=(records.get(month)||EMPTY_MONTH)[metric];
    const bar=node('progress');bar.max=max;bar.value=value??0;bar.setAttribute('aria-hidden','true');
    const label=node('span');
    if(value===null) {
      const dash=node('span','—');dash.setAttribute('aria-hidden','true');
      label.append(dash,node('span',tx('Not recorded'),'sr-only'));
    } else {
      label.textContent=isDuration?durationText(value):numberFormat.format(metric==='winRate'?value/100:value);
    }
    values.append(bar,label);
    row.append(node('dt',dateFormat.format(date)),values);chart.append(row);
  }
  $('month-page').textContent=tx('Page {0} of {1}',pages-page,pages);
  $('newer-months').disabled=page===0;
  $('older-months').disabled=page===pages-1;
  return page;
}

export function renderStatistics({rows,rowsBySport,catalog,selectedSport,scopeCount,includedCount,totalResults,onPlayer,onMatch,onSport}) {
  const summary=$('overall-summary'),overall=statistics(rows),nested=['tennis','padel'].includes(selectedSport);
  $('stats-title').textContent=sportText(selectedSport)+' · '+tx('Your record');
  const acrossSports=selectedSport==='all';
  const scope=[rows.length===scopeCount?
    tx(acrossSports?'All transferred matches for the selected mode across every sport.':'All transferred matches for the selected sport and mode.'):
    tx('{0} of {1} matches',rows.length,scopeCount)];
  if(Number.isInteger(totalResults)&&includedCount<totalResults) scope.push(tx('This transfer contains {0} of {1} saved matches. Open a full results link on the watch for your whole history.',includedCount,totalResults));
  $('statistics-scope').textContent=scope.join(' ');summary.replaceChildren();
  metric(summary,tx('Matches'),overall.played,record(overall));
  metric(summary,tx('Win rate'),overall.winRate===null?'—':overall.winRate+'%');
  if(!acrossSports) {
    metric(summary,tx(nested?'Sets':'Games'),overall.setsFor+' – '+overall.setsAgainst,tx('For · against'));
    if(nested) metric(summary,tx('Games'),overall.gamesFor+' – '+overall.gamesAgainst,tx('For · against'));
    const points=pointSummary(rows,overall);
    metric(summary,tx('Points'),points.value,points.note||tx('For · against'));
  }

  const recent=$('recent-form');recent.replaceChildren();
  for(const r of newest(rows).slice(0,12).reverse()) {
    const won=rowWinner(r)===0, b=node('button',tx(won?'W':'L'),won?'win':'loss');
    const opponent=catalog.get(r[4])?.name||r[5]||tx('Unnamed');
    b.title=tx('{0} against {1}',outcomeText(r),opponent)+' · '+momentText(r);
    b.setAttribute('aria-label',b.title);b.addEventListener('click',()=>onMatch(r[1]));recent.append(b);
  }
  if(!rows.length) recent.append(node('p',tx('No matches in this selection.'),'muted'));
  const insights=$('match-insights');insights.replaceChildren();
  if(rows.length) {
    const s=matchInsights(rows);
    metric(insights,tx('Current streak'),s.current,tx(s.last===0?'Consecutive wins':'Consecutive losses'));
    metric(insights,tx('Longest win streak'),s.best,tx('Consecutive wins'));
    if(s.deciding) metric(insights,tx(acrossSports?'Deciding matches won':nested?'Deciding sets won':'Deciding games won'),s.decidingWon+' / '+s.deciding,tx('Went the full distance'));
    if(s.timed) metric(insights,tx('Average duration'),durationText(s.averageSeconds),tx('{0} timed matches',s.timed));
  }
  const sportList=$('sport-records');sportList.replaceChildren();
  for(const sport of orderedSports(new Set(rowsBySport.map(rowSport)))) {
    const stat=statistics(rowsBySport.filter(r=>rowSport(r)===sport));
    recordButton(sportList,sportText(sport),tx('{0} matches',stat.played)+' · '+record(stat),stat.winRate,()=>onSport(sport),sportMark(sport));
  }
  if(!rowsBySport.length) sportList.append(node('p',tx('No matches in this selection.'),'muted'));
  renderPlayers(rows,catalog,'opponent',onPlayer,selectedSport);
  renderPlayers(rows,catalog,'partner',onPlayer,selectedSport);
}
