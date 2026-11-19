import {SPORTS,rowRules,scorePairs} from './sports.mjs?v=0.16.3';
import {localDate,name} from './interchange.mjs?v=0.16.0';
import {tx,sportText,errorText} from './transfer-copy.mjs?v=5';
import {node,orderedSports} from './transfer-view.mjs?v=6';
import {bestOptions,defaultRules,entryWinners,resultFromEntry,todayLocal} from './result-entry.mjs?v=2';
import {winMark} from './match-sheet.mjs?v=2';
import {addName} from './transfer-state.mjs?v=2';

export function mountResultEditor(dialog,onSave) {
  const field=id=>dialog.querySelector('#entry-'+id);
  let context=null,original=null,format=null,best=3,scoreValues=[];
  const form=dialog.querySelector('form');
  function options(key,values,selected) {
    const select=field(key);
    select.replaceChildren(...values.map(([value,label])=>{
      const option=node('option',label);option.value=String(value);return option;
    }));
    select.value=String(selected);
  }
  function rebuildRules() {
    const s=format.sport,nested=format.nested;
    const sportIds=orderedSports(context.sports||SPORTS);
    options('sport',sportIds.map(id=>[id,sportText(id)]),s);
    const modes=s==='squash'?[false]:s==='padel'?[true]:[false,true];
    options('mode',modes.map(v=>[+v,tx(v?'Doubles':'Singles')]),+format.doubles);
    const lengths=bestOptions(s);
    options('best',lengths.map(v=>[v,tx('Best of {0}',v)]),best);
    const targets=s==='badminton'?[21,15]:s==='pickleball'?[11,15,21]:[format.target];
    options('target',targets.map(v=>[v,String(v)]),format.target);
    field('target').closest('label').hidden=targets.length===1;
    const rules=nested?[['advantage','Advantage'],['no-ad',s==='padel'?'Golden Point':'No-Ad'],...(s==='padel'?[['star','Star Point']]:[])]:
      s==='pickleball'?[['side-out','Traditional side-out'],['rally-2025','Rally 2025'],['rally-2026','Rally 2026']]:[];
    options('deuce',rules.map(([id,label])=>[id,tx(label)]),format.deuce);
    field('deuce').closest('label').hidden=!rules.length;
    options('decider',[[0,tx('Full set')],[1,tx('Match tie-break')]],+format.finalTie);
    field('decider').closest('label').hidden=!nested;
    for(const key of ['sport','mode','best','target','deuce','decider'])field(key).disabled=!!original||field(key).options.length===1;
    for(const key of ['partner','other'])field(key).closest('label').hidden=!format.doubles;
    field('score-help').textContent=nested?tx('Enter games per set.'):s==='badminton'?tx('First to {0}, win by two, cap {1}.',format.target,format.target===21?30:21):tx('First to {0}, win by two.',format.target);
    drawScores();
  }
  function drawScores() {
    const grid=field('scores');
    grid.replaceChildren();
    const heading=node('span',tx(format.nested?'Set':'Game'),'score-col-head');
    grid.append(heading);
    for(const side of [0,1]){
      const team=node('span','','score-col-head');
      team.id='entry-team-'+side;
      grid.append(team);
    }
    for(let i=0;i<best;i++) {
      const label=format.finalTie&&i===best-1?tx('Match tie-break'):tx(format.nested?'Set {0}':'Game {0}',i+1);
      const period=node('span',label,'score-row-label');
      period.id='entry-period-'+i;
      grid.append(period);
      for(let side=0;side<2;side++){
        const cell=node('span',undefined,'entry-score');
        const el=node('input');el.type='number';el.inputMode='numeric';el.min='0';el.max='10000';el.step='1';
        el.setAttribute('aria-labelledby',period.id+' entry-team-'+side);
        el.dataset.index=i;el.dataset.side=side;el.value=scoreValues[i]?.[side]??'';
        cell.append(el,winMark());grid.append(cell);
      }
    }
    refreshTeams();
    refreshWinners();
  }
  function refreshWinners() {
    const winners=entryWinners(format,best,scoreValues);
    for(const input of field('scores').querySelectorAll('input')){
      const won=winners[Number(input.dataset.index)]===Number(input.dataset.side);
      input.parentElement.classList.toggle('is-winner',won);
      if(won)input.setAttribute('aria-describedby','entry-won');
      else input.removeAttribute('aria-describedby');
    }
  }
  field('scores').addEventListener('input',ev=>{
    const {index,side}=ev.target.dataset;
    if(index===undefined)return;
    scoreValues[index]??=['',''];scoreValues[index][side]=ev.target.value;
    refreshWinners();
  });
  function refreshTeams() {
    const heads=field('scores').querySelectorAll('.score-col-head');
    if(heads.length!==3)return;
    heads[1].textContent=[context.myName||tx('Me'),format.doubles&&field('partner').value].filter(Boolean).join(' + ');
    heads[2].textContent=[field('opponent').value||tx('Opponent'),format.doubles&&field('other').value].filter(Boolean).join(' + ');
  }
  for(const key of ['opponent','partner','other'])field(key).addEventListener('input',refreshTeams);
  field('sport').onchange=()=>{format=defaultRules(field('sport').value);best=3;rebuildRules();};
  field('mode').onchange=()=>{format.doubles=field('mode').value==='1';rebuildRules();};
  field('best').onchange=()=>{best=Number(field('best').value);rebuildRules();};
  field('target').onchange=()=>{format.target=Number(field('target').value);rebuildRules();};
  field('deuce').onchange=()=>{format.deuce=field('deuce').value;};
  field('decider').onchange=()=>{format.finalTie=field('decider').value==='1';drawScores();};
  field('cancel').onclick=()=>dialog.close();
  function open(settings) {
    context=settings;original=settings.row||null;form.reset();field('error').hidden=true;
    format=original?rowRules(original):defaultRules(settings.sport);
    if(!original&&!['squash','padel'].includes(format.sport)&&settings.mode!=='all')format.doubles=settings.mode==='doubles';
    best=original?Number(original[6]):3;
    scoreValues=original?scorePairs(original).map(p=>p?p.map(String):['','']):[];
    field('title').textContent=tx(original?'Edit result':'Add result');
    const timed=!!(original?.[7]||original?.[8]);
    field('date').disabled=timed;field('date').max=todayLocal();
    field('date').value=original?localDate(original):todayLocal();
    field('date-note').hidden=!timed;
    const players=new Map(settings.catalog.map(p=>[p.id,p]));
    field('names').replaceChildren(...settings.catalog.filter(p=>!p.archived).map(p=>{const o=node('option');o.value=p.name;return o;}));
    for(const [i,key] of ['opponent','partner','other'].entries()){
      const col=[4,32,34][i];field(key).value=original?(players.get(original[col])?.name||original[col+1]||''):'';
    }
    field('retired').value=original?.[23]||'';field('retired').closest('label').hidden=!settings.retirement;
    rebuildRules();dialog.showModal();dialog.scrollTop=0;field('title').focus({preventScroll:true});
  }
  form.addEventListener('submit',ev=>{
    ev.preventDefault();
    try {
      const catalog=context.catalog.map(person=>({...person}));
      const players=['opponent','partner','other'].map((key,i)=>{
        const raw=field(key).value.trim();
        if(!raw||i>0&&!format.doubles)return null;
        const value=name(raw),found=catalog.find(p=>p.name.toLowerCase()===value.toLowerCase());
        return found||addName(catalog,value);
      });
      const row=resultFromEntry({original,baseline:context.baseline||original,id:context.id,date:field('date').value,
        rules:format,best,players,scores:scoreValues.slice(0,best),retired:field('retired').value,reference:context.reference});
      onSave(row);dialog.close();
    } catch(e) {
      const error=field('error');
      try{error.textContent=tx(e.message);}catch{error.textContent=errorText(e);}
      error.hidden=false;
      const input=e.scoreIndex===undefined?error:field('scores').querySelector('[data-index="'+e.scoreIndex+'"]');
      (input||error).focus();error.scrollIntoView({block:'nearest'});
    }
  });
  dialog.addEventListener('close',()=>{context=null;original=null;scoreValues=[];form.reset();field('names').replaceChildren();field('scores').replaceChildren();});
  return {open,close:()=>{if(dialog.open)dialog.close();}};
}
