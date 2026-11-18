import {mountLanguage,language} from './locale.mjs';
import {validateBackup,datedFile} from './backup.mjs?v=0.16.0';
import {SPORTS,sportMarkup,rowSport} from './sports.mjs?v=0.16.3';
import {open,seal,name,parseCsv,writeCsv,HEADER,statistics,localDate} from './interchange.mjs?v=0.16.0';
import {addName,incomingPayload,checkRetirementSupport,initialView,filteredRows,sportsByPlayer,inScope,filterPlayers} from './transfer-state.mjs?v=2';
import {newest,resultCard,shortRecord,renderStatistics,orderedSports,node} from './transfer-view.mjs?v=3';
import {tx,errorText,sportText,momentText} from './transfer-copy.mjs';

const $=id=>document.getElementById(id);
const PAGE_SIZE=25;
const pageTitles={
  results:['Match journal','Browse history and scores'], statistics:['Match insights','Compare sports and players'],
  opponents:['Player directory','Manage names on your watch'],import:['Import CSV','Bring saved results to your watch'],
  backup:['Backup','Save or restore your watch data'],
};
let link=null, snapshot=null, expiry=0, tabId='results', resultPage=0, catalog=[], directory=new Map(), sportsIndex=new Map(), rows=[];
let importCsv='', importRows=[], restoreBackup='', restoreDate=null, sent=false, sending=false;
let myName='', invalidOwnName=false, current='', changeCurrent=false, editPerson=null, editValue='';
let notice=['On your watch, open Results → Options → View results on phone, or Players → Edit names on phone. Scan the QR code.'], error=false;
function setNotice(message='',isError=false) {
  notice=message?[message]:[];error=isError;renderNotice();
}
function renderNotice() {
  const element=$('status');
  element.hidden=!notice.length;element.classList.toggle('error',error);
  element.textContent=notice.length?(error?errorText(notice[0]):tx(...notice)):'';
}
function report(err) {setNotice(err,true);}
function tryAction(fn) {try{return fn();}catch(err){report(err);}}
function option(select,value,label){const entry=node('option',label);entry.value=value;select.append(entry);}
const byName=(a,b)=>a.name.localeCompare(b.name,language());
const playerName=(id,fallback='')=>directory.get(id)?.name||fallback;
function roleName(entry){return entry.archived?tx('{0} (archived)',entry.name):entry.name;}
function initials(value){return value.trim().split(/\s+/u).slice(0,2).map(s=>[...s][0]||'').join('').toLocaleUpperCase(language());}
function fillPlayers() {
  directory=new Map(catalog.map(e=>[e.id,e]));
  const filter=$('opponent-filter'),previous=filter.value,sorted=[...catalog].sort(byName);
  filter.replaceChildren();option(filter,'*',tx('All players'));option(filter,'',tx('Unnamed'));
  const candidates=filterPlayers(catalog,rows).sort(byName);
  for(const e of candidates) option(filter,e.id,roleName(e));
  filter.value=candidates.some(e=>e.id===previous)||previous===''?previous:'*';
  const picker=$('current-opponent');picker.replaceChildren();option(picker,'',tx('No name'));
  for(const e of sorted) if(!e.archived||e.id===current) option(picker,e.id,roleName(e));
  picker.value=current;picker.disabled=sent||sending||!changeCurrent;
}
function contextRows() {return inScope(rows,$('sport-select').value,$('mode-select').value);}
function filters() {
  return {id:$('opponent-filter').value,role:$('role-filter').value,outcome:$('outcome-filter').value,from:$('from').value,to:$('to').value};
}
function exportNames(row) {
  const copy=[...row];
  for(const index of row[0]==='3'?[4,32,34]:[4]) if(copy[index]) copy[index+1]=playerName(copy[index],copy[index+1]);
  return copy;
}
function renderResults(reset=true) {
  const f=filters(),list=$('result-list'),context=contextRows(),active=[f.id!=='*',Boolean(f.id!=='*'&&f.role!=='all'),f.outcome!=='all',!!f.from,!!f.to,$('mode-select').value!=='all'].filter(Boolean).length;
  $('role-field').hidden=f.id==='*';
  $('filters-applied').textContent=active?tx('{0} selected',active):'';
  let selected;
  try {selected=newest(filteredRows(context,f));} catch(err) {
    list.replaceChildren();$('filtered-summary').textContent=errorText(err);$('count').hidden=true;
    $('result-pages').hidden=true;$('download').disabled=true;return;
  }
  $('download').disabled=!selected.length;
  if(reset) resultPage=0;
  const pages=Math.max(1,Math.ceil(selected.length/PAGE_SIZE));
  resultPage=Math.max(0,Math.min(resultPage,pages-1));
  $('count').hidden=selected.length===context.length;
  $('count').textContent=tx('{0} of {1} matches',selected.length,context.length);
  $('filtered-summary').textContent=shortRecord(statistics(selected));
  list.replaceChildren();
  const monthFormat=new Intl.DateTimeFormat(language(),{month:'long',year:'numeric',timeZone:'UTC'});
  let lastMonth='',group;
  for(const row of selected.slice(resultPage*PAGE_SIZE,(resultPage+1)*PAGE_SIZE)) {
    const month=localDate(row).slice(0,7);
    if(month!==lastMonth) {
      group=node('div',undefined,'ledger-group');
      list.append(node('h3',monthFormat.format(new Date(month+'-01T12:00:00Z')),'ledger-period'),group);
      lastMonth=month;
    }
    const r=exportNames(row);group.append(resultCard(r,r[5]||tx('Opponent'),myName||tx('Me')));
  }
  if(!selected.length) list.append(node('p',tx(rows.length?'No matches fit these filters.':'No saved matches in this transfer.'),'empty'));
  $('result-pages').hidden=pages<=1;$('result-page').textContent=tx('Page {0} of {1}',resultPage+1,pages);
  $('prev-results').disabled=resultPage===0;$('next-results').disabled=resultPage>=pages-1;
}
function showMatches(id='*',role='all',matchId=null) {
  $('opponent-filter').value=id;$('role-filter').value=role;
  $('outcome-filter').value='all';$('from').value='';$('to').value='';
  const index=matchId?newest(filteredRows(contextRows(),filters())).findIndex(r=>r[1]===matchId):0;
  resultPage=Math.floor(Math.max(0,index)/PAGE_SIZE);
  if(id!=='*') $('result-filters').open=true;
  showTab('results');renderResults(false);
  if(matchId) {
    for(const detail of document.querySelectorAll('details.result')) if(detail.dataset.matchId===matchId){
      detail.open=true;detail.querySelector('summary').focus({preventScroll:true});detail.scrollIntoView({block:'start'});
    }
  } else {
    $('history-heading').focus({preventScroll:true});$('history-heading').scrollIntoView({block:'start'});
  }
}
function renderStats(){
  renderStatistics({rows:contextRows(),allRows:rows,catalog:directory,mode:$('mode-select').value,selectedSport:$('sport-select').value,totalResults:snapshot.totalResults,
    onPlayer:(id,role)=>showMatches(id,role),onMatch:id=>showMatches('*','all',id),onSport:sport=>{$('sport-select').value=sport;renderResults();renderStats();$('stats-title').scrollIntoView({block:'start'});}
  });
}
function stagedNames() {
  return snapshot&&(JSON.stringify(catalog)!==JSON.stringify(snapshot.opponents)||changeCurrent&&current!==(snapshot.currentOpponentId||'')||myName!==(snapshot.myName||''));
}
function renderDraft() {
  if(!snapshot) return;
  const names=stagedNames(),modified=!!(names||importCsv),renaming=unfinishedRename();
  const sendBar=$('send-changes');
  sendBar.hidden=tabId==='backup'||(!(tabId==='opponents'||tabId==='import')&&!modified&&!renaming&&!sent);
  sendBar.classList.toggle('pending',(modified||renaming)&&!sent);
  const edits=[names?tx('These names or assignments have been edited.'):'',importCsv?tx('CSV results ready to send: {0}',importRows.length):''].filter(Boolean).join(' ');
  $('changes').textContent=sent?tx('Changes sent.'):renaming?tx('Save or cancel the open name edit before sending.'):edits||tx('No changes prepared.');
  $('send').textContent=tx(renaming?'Review name':'Send changes');
  $('send').disabled=sent||sending||!renaming&&(!modified||invalidOwnName);
  $('change-current').disabled=sent||sending;$('new-name').disabled=sent||sending;$('add-name').disabled=sent||sending;
  $('my-name').disabled=sent||sending;$('csv-file').disabled=sent||sending;$('clear-import').disabled=sent||sending||!importCsv;
  $('clear-import').hidden=!importCsv;
  $('backup-file').disabled=sent||sending||!snapshot.supportsFullBackup;$('restore-send').disabled=sent||sending||!restoreBackup;
  $('current-opponent').disabled=sent||sending||!changeCurrent;
  $('import-preview').textContent=importCsv?tx('{0} completed matches ready for review.',importRows.length):tx('No file selected.');
  $('backup-preview').textContent=restoreDate?tx('Backup created {0}. Review the restore on your watch.',new Date(restoreDate).toLocaleString(language())):tx('No file selected.');
}
function unfinishedRename(){return editPerson!==null&&editValue!==directory.get(editPerson)?.name;}
function renderImportGames() {
  const preview=$('import-games');
  preview.replaceChildren();preview.hidden=!importRows.length;
  if(preview.hidden)return;
  const shown=importRows.slice(0,5);
  const table=node('table',undefined,'import-games');
  const head=table.createTHead().insertRow(),body=table.createTBody();
  for(const title of ['Match','Opponent','Final result']){
    const cell=node('th',tx(title));cell.scope='col';head.append(cell);
  }
  for(const row of shown){
    const line=body.insertRow(),match=line.insertCell();
    match.append(node('strong',sportText(rowSport(row))),node('small',momentText(row,7,false)));
    const opponent=row[5]||tx('Opponent');
    line.insertCell().textContent=row[25]==='doubles'?opponent+' + '+(row[35]||tx('Opponent 2')):opponent;
    line.insertCell().textContent=row[11]+' – '+row[12];
  }
  preview.append(table);
  if(shown.length<importRows.length)preview.append(node('p',tx('Showing the first {0} of {1} matches.',shown.length,importRows.length),'muted'));
}
function makeAction(text,style,fn) {
  const b=node('button',tx(text),style);b.type='button';b.disabled=sent||sending;
  b.addEventListener('click',()=>tryAction(fn));return b;
}
function applied() {editPerson=null;editValue='';fillPlayers();renderPeople();renderResults(false);renderStats();renderDraft();setNotice('Changes prepared for your watch.');}
function searchKey(value) {return value.normalize('NFD').replace(/\p{Mark}/gu,'').toLocaleLowerCase(language()).trim();}
function renderPeople() {
  const list=$('opponent-list'),query=searchKey($('player-search').value);
  const available=catalog.filter(e=>!e.archived||$('show-removed').checked);
  const people=available.filter(e=>!query||searchKey(e.name).includes(query)).sort(byName);
  list.replaceChildren();
  $('player-count').textContent=query?people.length+' / '+available.length:people.length;
  if(!people.length) list.append(node('p',tx(query?'No players match your search.':catalog.length?'No active players.':'No saved players yet.'),'empty'));
  for(const e of people) {
    const row=node('article',undefined,'person');
    const avatar=node('span',initials(e.name),'avatar');avatar.setAttribute('aria-hidden','true');
    const title=node('div',undefined,'person-label');title.append(node('strong',e.name));
    const sportIds=orderedSports(new Set([...(e.sports||[]),...(sportsIndex.get(e.id)||[])].filter(s=>SPORTS.includes(s))));
    const badges=node('div',undefined,'sport-badges');
    for(const sport of sportIds){const mark=node('span');mark.dataset.sport=sport;mark.innerHTML=sportMarkup(sport);mark.append(document.createTextNode(sportText(sport)));badges.append(mark);}
    if(e.archived) title.append(node('small',tx('Archived'),'muted'));
    if(sportIds.length) title.append(badges);
    const control=e.archived?makeAction('Restore player','quiet',()=>{e.archived=false;applied();}):makeAction('Edit','quiet',()=>{
      editPerson=e.id;editValue=e.name;renderPeople();const field=$('edit-player-name');field.focus();field.select();
    });
    row.append(avatar,title,control);
    if(editPerson===e.id&&!e.archived) {
      control.hidden=true;
      const form=node('form',undefined,'rename');
      const label=node('label',tx('Name for {0}',e.name)),field=node('input');field.id='edit-player-name';field.maxLength=120;field.value=editValue;field.required=true;field.autocomplete='off';
      field.addEventListener('input',()=>{editValue=field.value;renderDraft();});label.append(field);
      const actions=node('div',undefined,'actions'),save=node('button',tx('Save name'),'people-action');save.type='submit';save.disabled=sent||sending;
      const archive=makeAction('Archive','danger-text',()=>{if(confirm(tx('Archive {0}? Their past results will keep this name.',e.name))){e.archived=true;applied();}});
      const cancel=makeAction('Cancel','quiet',()=>{editPerson=null;editValue='';renderPeople();renderDraft();});
      actions.append(save,cancel,archive);form.append(label,actions);
      form.addEventListener('submit',ev=>{ev.preventDefault();if(sent||sending)return;tryAction(()=>{
        const next=name(field.value);
        if(catalog.some(other=>other.id!==e.id&&other.name.toLowerCase()===next.toLowerCase())) throw Error('duplicate');
        e.name=next;applied();
      });});
      row.append(form);
    }
    list.append(row);
  }
}
function renderHeading() {
  const [title,subtitle]=snapshot?pageTitles[tabId]:['Match journal','Results from your watch'];
  $('page-title').textContent=tx(title);$('page-subtitle').textContent=tx(subtitle);
  document.title='Keep Score · '+tx(title);
}
function showTab(id) {
  if(id==='backup'&&!snapshot?.supportsFullBackup) id='results';
  tabId=id;renderHeading();
  const tools=$('journal-tools');tools.classList.toggle('active',id==='import'||id==='backup');
  if(tools.open){
    if(tools.contains(document.activeElement)) tools.querySelector('summary').focus({preventScroll:true});
    tools.open=false;
  }
  document.querySelector('.sport-scope').hidden=!['results','statistics'].includes(id);
  for(const b of document.querySelectorAll('[data-tab]')) {
    const selected=b.dataset.tab===id;b.classList.toggle('selected',selected);
    if(selected) b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');
  }
  for(const page of Object.keys(pageTitles)) $(page).hidden=id!==page;
  renderDraft();
}
function renderLanguage() {
  document.documentElement.lang=language();
  for(const el of document.querySelectorAll('[data-i]')) el.textContent=tx(el.dataset.i);
  for(const el of document.querySelectorAll('[data-placeholder]')) el.placeholder=tx(el.dataset.placeholder);
  for(const el of document.querySelectorAll('[data-aria]')) el.setAttribute('aria-label',tx(el.dataset.aria));
  renderHeading();renderNotice();
  if(!snapshot) return;
  const selected=$('sport-select').value||snapshot.selectedSport||'squash',focus=document.querySelector('details.result[open]')?.dataset.matchId;
  $('sport-select').replaceChildren();option($('sport-select'),'all',tx('All sports'));for(const s of orderedSports()) option($('sport-select'),s,sportText(s));
  $('sport-select').value=selected==='all'||SPORTS.includes(selected)?selected:'squash';
  $('expiry').textContent=tx('Expires at {0}',new Date(expiry).toLocaleTimeString(language(),{hour:'2-digit',minute:'2-digit'}));
  $('backup-help').textContent=tx(snapshot.fullBackup?'Keep this complete backup file private.':'To download a backup, open Settings → Backup & restore on the watch.');
  $('match-assignment-help').textContent=tx(snapshot.currentMatchComplete===true?'Changes here apply to your last completed match.':snapshot.currentMatchComplete===false?'Changes here apply to your current match.':'Changes here apply to the current or last completed match on your watch.');
  fillPlayers();renderPeople();renderResults(false);renderStats();renderImportGames();renderDraft();showTab(tabId);
  if(focus) for(const detail of document.querySelectorAll('details.result')) if(detail.dataset.matchId===focus) detail.open=true;
}
function download(file,text,type='text/csv;charset=utf-8'){
  const url=URL.createObjectURL(new Blob([text],{type})),a=document.createElement('a');a.href=url;a.download=file;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
async function api(path,options={}) {
  if(!link) throw Error('link');
  const response=await fetch('/api/transfer/'+link.id+path,{...options,cache:'no-store',credentials:'omit',headers:{Authorization:'Bearer '+link.token,'Content-Type':'application/json'}});
  if(!response.ok) {
    const value=await response.json().catch(()=>({}));
    if(response.status===404||response.status===410) throw Error('Transfer expired or ended');
    throw Error(value.error||(response.status>=500?'busy':'failed'));
  }
  return response.json();
}
function expire(){
  link=null;snapshot=null;catalog=[];rows=[];directory.clear();sportsIndex.clear();restoreBackup='';restoreDate=null;
  myName='';editPerson=null;editValue='';importCsv='';importRows=[];current='';changeCurrent=false;expiry=0;
  $('workspace').replaceChildren();$('workspace').hidden=true;$('expiry').hidden=true;renderHeading();
  setNotice('This transfer has expired. Scan a new QR code on your watch.');
}
async function sendIncoming(backup=false) {
  if(!snapshot||sent||sending||(!backup&&(invalidOwnName||unfinishedRename()))) return;
  const session=link;
  sending=true;renderDraft();renderPeople();
  try {
    const payload=backup?{schema:1,sessionId:session.id,baseDirectory:snapshot.baseDirectory,currentMatchId:snapshot.currentMatchId,restoreBackup}:incomingPayload({snapshot,catalog,current,changeCurrent,csv:importCsv,sessionId:session.id,myName});
    const cipher=await seal(session.id,'in',session.key,JSON.stringify(payload),undefined,backup);
    if(link!==session) return;
    await api('/incoming',{method:'POST',body:JSON.stringify({cipher})});
    if(link!==session) return;
    sent=true;editPerson=null;editValue='';restoreBackup='';
    setNotice(backup?'Backup sent. Review and confirm it on your watch.':'Changes sent.');
  } catch(err){if(link===session)report(err);}
  finally {sending=false;if(link===session){renderDraft();renderPeople();}}
}
for(const b of document.querySelectorAll('[data-tab]')) b.addEventListener('click',()=>showTab(b.dataset.tab));
document.addEventListener('click',ev=>{
  const tools=$('journal-tools');if(tools?.open&&!tools.contains(ev.target))tools.open=false;
});
document.addEventListener('keydown',ev=>{
  const tools=$('journal-tools');
  if(ev.key==='Escape'&&tools?.open){tools.open=false;tools.querySelector('summary').focus();}
});
const wideFilters=matchMedia('(min-width:720px)');
function fitFilters(){const filters=$('result-filters');if(filters)filters.open=wideFilters.matches;}
fitFilters();wideFilters.addEventListener('change',fitFilters);
for(const id of ['sport-select','mode-select']) $(id).addEventListener('change',()=>{renderResults();renderStats();});
for(const id of ['opponent-filter','role-filter','outcome-filter','from','to']) $(id).addEventListener('change',()=>{
  if(id==='opponent-filter') $('role-filter').value='all';
  renderResults();
});
for(const [id,step] of [['prev-results',-1],['next-results',1]]) $(id).addEventListener('click',()=>{
  resultPage+=step;renderResults(false);$('history-heading').focus({preventScroll:true});$('history-heading').scrollIntoView({block:'start'});
});
$('view-all-history').addEventListener('click',()=>showMatches());
$('reset-filter').addEventListener('click',()=>{
  $('mode-select').value='all';$('opponent-filter').value='*';$('role-filter').value='all';
  $('outcome-filter').value='all';$('from').value='';$('to').value='';renderResults();renderStats();
});
$('show-removed').addEventListener('change',renderPeople);
$('player-search').addEventListener('input',renderPeople);
$('add-person').addEventListener('submit',ev=>{ev.preventDefault();if(sent||sending)return;tryAction(()=>{
  addName(catalog,$('new-name').value);$('new-name').value='';applied();$('new-name').focus();
});});
$('my-name').addEventListener('input',()=>{
  try{const value=$('my-name').value.trim();myName=value?name(value):'';invalidOwnName=false;
    $('my-initial').textContent=initials(myName)||'●';$('my-name').removeAttribute('aria-invalid');setNotice();
  }catch(err){invalidOwnName=true;$('my-name').setAttribute('aria-invalid','true');report(err);}
  renderDraft();
});
$('my-name').addEventListener('change',()=>{if(!invalidOwnName)renderResults(false);});
$('change-current').addEventListener('change',()=>{
  if(sent||sending)return;changeCurrent=$('change-current').checked;
  if(!changeCurrent) current=snapshot.currentOpponentId||'';fillPlayers();renderDraft();
});
$('current-opponent').addEventListener('change',()=>{if(changeCurrent&&!sent&&!sending){current=$('current-opponent').value;renderDraft();}});
$('download').addEventListener('click',()=>tryAction(()=>{
  const selected=newest(filteredRows(contextRows(),filters())).map(exportNames);
  download(datedFile($('sport-select').value+'_results.csv'),writeCsv(selected));
}));
$('template').addEventListener('click',()=>download(datedFile('template.csv'),'\ufeff'+HEADER+'\r\n'));
$('csv-file').addEventListener('change',async()=>{
  const session=link,field=$('csv-file'),file=field.files[0];
  try {
    if(!file||!session)return;if(file.size>3*1024*1024)throw Error('csvSize');
    const text=await file.text();if(link!==session||field.files[0]!==file)return;
    const parsed=parseCsv(text);checkRetirementSupport(snapshot,parsed);
    if(!snapshot.supportsPickleball&&parsed.some(r=>rowSport(r)==='pickleball'))throw Error('pickle');
    if(!snapshot.supportsMultisport&&parsed.some(r=>r[0]==='3'))throw Error('multi');
    importCsv=text;importRows=parsed;renderImportGames();renderDraft();setNotice('Changes prepared for your watch.');
  }catch(err){if(link!==session||field.files[0]!==file)return;importCsv='';importRows=[];field.value='';renderImportGames();renderDraft();report(err);}
});
$('clear-import').addEventListener('click',()=>{importCsv='';importRows=[];$('csv-file').value='';renderImportGames();renderDraft();setNotice();});
$('send').addEventListener('click',()=>{
  if(!unfinishedRename()){sendIncoming();return;}
  showTab('opponents');$('player-search').value='';renderPeople();$('edit-player-name').focus();
});
$('download-backup').addEventListener('click',()=>tryAction(()=>{
  if(!snapshot?.fullBackup)throw Error('backup');
  download(datedFile('Backup.json'),snapshot.fullBackup,'application/json');setNotice('Complete backup downloaded.');
}));
$('backup-file').addEventListener('change',async()=>{
  const session=link,field=$('backup-file'),file=field.files[0];
  restoreBackup='';restoreDate=null;renderDraft();
  try{
    if(!session||!file)return;if(file.size>20*1024*1024)throw Error('backupSize');
    const backup=await validateBackup(await file.text());if(link!==session||field.files[0]!==file)return;
    restoreBackup=backup.text;restoreDate=backup.createdAt;renderDraft();setNotice();
  }catch(err){if(link===session)report(err);}
});
$('restore-send').addEventListener('click',()=>{if(snapshot?.supportsFullBackup&&restoreBackup)sendIncoming(true);});
async function initialize(){
  try {
    const p=new URLSearchParams(location.hash.slice(1)),focus=p.get('match');
    tabId=initialView(p.get('view'));const candidate={id:p.get('id'),token:p.get('p'),key:p.get('k')};
    history.replaceState(null,'',location.pathname);if(!candidate.id&&!candidate.token&&!candidate.key)return;
    if(!/^[a-f0-9]{32}$/.test(candidate.id)||!/^[-\w]{43}$/.test(candidate.token)||!/^[-\w]{43}$/.test(candidate.key))throw Error('link');
    if(!crypto.subtle)throw Error('crypto');link=candidate;setNotice('Opening encrypted transfer…');
    const data=await api('/outgoing');const incoming=JSON.parse(await open(link.id,'out',link.key,data.cipher));
    if(incoming.schema!==1||incoming.sessionId!==link.id||!Array.isArray(incoming.opponents)||incoming.opponents.length>1000||!Number.isFinite(data.expiresAt))throw Error('unsupported');
    const names=incoming.opponents.map(e=>({...e,name:name(e.name),archived:!!e.archived})),matches=parseCsv(incoming.csv);
    if(data.expiresAt<=Date.now()){expire();return;}
    snapshot=incoming;expiry=data.expiresAt;catalog=names;rows=matches;sportsIndex=sportsByPlayer(rows);current=snapshot.currentOpponentId||'';
    myName=snapshot.myName||'';$('my-name').value=myName;$('my-initial').textContent=initials(myName)||'●';
    $('your-name-section').hidden=!snapshot.supportsYourName;$('backup-tab').hidden=!snapshot.supportsFullBackup;
    $('download-backup').disabled=!snapshot.fullBackup;$('workspace').hidden=false;$('expiry').hidden=false;
    setTimeout(expire,Math.max(0,expiry-Date.now()));setNotice();renderLanguage();
    if(focus&&rows.some(r=>r[1]===focus)) {$('sport-select').value=rowSport(rows.find(r=>r[1]===focus));renderStats();showMatches('*','all',focus);}
  }catch(err){report(err);}
}
mountLanguage($('language-control'),renderLanguage);
renderLanguage();
initialize();
