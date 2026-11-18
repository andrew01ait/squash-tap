import {t, mountLanguage} from './locale.mjs';
import {renderScorecard} from './match-sheet.mjs';
import {applyAction,readSnapshot} from './referee-scoring.mjs';
import {refereeLink,roomKey,sealSnapshot,openSnapshot,storage,transport,acceptRemote,dirty,
  updateLocal,synchronize,resolveConflict,rememberUploadToken,isClosed,clearExpiredCopies} from './referee-store.mjs';

const el=id=>document.getElementById(id);
const link=refereeLink(location.hash);
let key,api,registration,channel,active,local,match,remote;
let busy=null,queue=Promise.resolve(),readNumber=0,timer,backoff=2000;
let connection='loading',offlineShell=false,backgroundEnabled=false,storageFailed=false,failedToOpen=false;
const labels=[
  ()=>t('Player 1','Spieler 1','Joueur 1','Jugador 1'),
  ()=>t('Player 2','Spieler 2','Joueur 2','Jugador 2'),
  ()=>t('Partner','Partner','Partenaire','Pareja'),
  ()=>t('Second opponent','Zweiter Gegner','Deuxième adversaire','Segundo rival')
];
const sports={badminton:['Badminton','Badminton','Badminton','Bádminton'],padel:['Padel','Padel','Padel','Pádel'],
  pickleball:['Pickleball','Pickleball','Pickleball','Pickleball'],squash:['Squash','Squash','Squash','Squash'],
  tennis:['Tennis','Tennis','Tennis','Tenis'],'table-tennis':['Table tennis','Tischtennis','Tennis de table','Tenis de mesa']};
const slot=(i,data=local)=>{
  const label=data?.labels[i];
  if(!label)return labels[i]();
  return label;
};
const sideName=(i,m=match,data=local)=>m.format.doubles?slot(i,data)+' + '+slot(i+2,data):slot(i,data);
const workerSupported='serviceWorker' in navigator;
const online=()=>navigator.onLine!==false;
function button(id,text,fn){const b=el(id);b.textContent=text;if(fn)b.onclick=fn;}
const errorText=text=>{el('action-message').textContent=text;};
function fillFixedCopy(){
  el('title').textContent=t('Referee','Schiedsrichter','Arbitrage','Arbitraje');
  document.title=el('title').textContent+' · Keep Score';
  el('board').setAttribute('aria-label',t('Match','Partie','Match','Partido'));
  el('sheet-title').textContent=t('Scorecard','Satzverlauf','Détail du score','Parciales');
  button('choose-starters',t('Choose starting players','Startspieler auswählen','Choisir les premiers joueurs','Elegir jugadores iniciales'),()=>{
    el('server-settings').open=true;
    el('first').focus();
  });
  button('retry',t('Try again','Erneut versuchen','Réessayer','Reintentar'),()=>{if(storageFailed){location.reload();return;}schedule(0);});
  button('undo',t('Undo','Zurücknehmen','Annuler','Deshacer'),()=>dispatch({type:'undo'}));
  button('advance',t('Next game','Nächster Satz','Manche suivante','Siguiente juego'),()=>dispatch({type:'next'}));
  button('finalize',t('Confirm final result','Endergebnis bestätigen','Confirmer le résultat','Confirmar el resultado'),()=>dispatch({type:'finish'}));
  el('server-title').textContent=t('Starting server','Erster Aufschlag','Premier serveur','Primer sacador');
  el('first-label').textContent=t('First server','Erster Aufschläger','Premier serveur','Primer sacador');
  el('other-label').textContent=t('Starter on the other team','Startspieler im anderen Team','Premier joueur adverse','Primer jugador rival');
  button('set-server',t('Set service order','Aufschlagreihenfolge festlegen','Définir l’ordre de service','Guardar orden de saque'),()=>dispatch({type:'starters',first:Number(el('first').value),other:Number(el('other').value)}));
  el('retire-summary').textContent=t('End by retirement','Partie durch Aufgabe beenden','Terminer par abandon','Finalizar por retirada');
  el('retire-label').textContent=t('Retiring side','Wer gibt auf?','Camp qui abandonne','¿Quién se retira?');
  button('retire',t('Record retirement','Aufgabe erfassen','Enregistrer l’abandon','Registrar retirada'),()=>dispatch({type:'retire',side:Number(el('retire-side').value)}));
  el('conflict-title').textContent=t('Two scores need review','Zwei Spielstände zur Auswahl','Deux scores à vérifier','Hay dos marcadores distintos');
  el('conflict-text').textContent=t('Another referee device uploaded a different score. Choose the score to continue with.','Ein anderes Schiedsrichtergerät hat einen anderen Stand übertragen. Wähle den gültigen Spielstand.','Un autre appareil d’arbitrage a transmis un score différent. Choisissez celui à garder.','Otro dispositivo de arbitraje ha enviado un marcador distinto. Elige el válido.');
  button('take-remote',t('Use synced score','Synchronisierten Stand übernehmen','Utiliser le score synchronisé','Usar el marcador sincronizado'),()=>resolve(false));
  button('keep-local',t('Use this phone’s score','Spielstand dieses Handys übernehmen','Utiliser le score de ce téléphone','Usar el marcador de este móvil'),()=>resolve(true));
}
function stateCopy(){
  let hint,badge,mode='synced',retry=false;
  if(!link){
    hint=t('Scan the referee QR on the host watch to open this match.','Scanne den Schiedsrichter-QR auf der Uhr des Gastgebers, um die Partie zu öffnen.','Scannez le QR d’arbitrage sur la montre hôte pour ouvrir le match.','Escanea el QR de arbitraje del reloj anfitrión para abrir el partido.');
  }else if(storageFailed){
    hint=t('This browser could not save the score on this phone. Free some storage, then reopen this link.','Dieser Browser konnte den Stand nicht auf dem Handy speichern. Gib Speicherplatz frei und öffne den Link erneut.','Le navigateur n’a pas pu enregistrer le score sur ce téléphone. Libérez de l’espace puis rouvrez ce lien.','El navegador no pudo guardar el marcador en este móvil. Libera espacio y vuelve a abrir el enlace.');
    mode='error';retry=true;
  }else if(active?.unavailable||connection==='closed'){
    hint=active&&dirty(active)?t('The link has closed. This phone still holds your unsent score.','Der Link ist abgelaufen. Dein ungesendeter Spielstand ist noch auf diesem Handy.','Le lien a expiré. Le score non transmis reste sur ce téléphone.','El enlace ha caducado. Este móvil conserva el marcador sin enviar.'):
      t('This referee link has expired or closed. Scan the current QR on the host watch.','Dieser Schiedsrichterlink ist abgelaufen oder beendet. Scanne den aktuellen QR auf der Gastgeberuhr.','Ce lien d’arbitrage a expiré ou a été fermé. Scannez le QR actuel sur la montre hôte.','Este enlace de arbitraje ha caducado o se ha cerrado. Escanea el QR actual del reloj anfitrión.');
    mode='error';badge=t('Link closed','Link beendet','Lien fermé','Enlace cerrado');
  }else if(active?.conflict){
    badge=t('Review score','Stand prüfen','Vérifier le score','Revisar marcador');hint=el('conflict-text').textContent;mode='error';
  }else if(!active){
    if(failedToOpen||!online()){
      hint=t('Open this QR once with internet access to load the match. After that you can score offline.','Öffne diesen QR einmal mit Internet, um die Partie zu laden. Danach kannst du offline zählen.','Ouvrez ce QR une fois avec Internet pour charger le match. L’arbitrage fonctionne ensuite hors ligne.','Abre este QR una vez con conexión para cargar el partido. Después podrás arbitrar sin conexión.');retry=true;
    }else hint=t('Opening the match…','Partie wird geöffnet …','Ouverture du match…','Abriendo el partido…');
  }else if(connection==='offline'||!online()){
    badge=t('Offline','Offline','Hors ligne','Sin conexión');mode='offline';
    hint=local?.final?t('Final result saved on this phone. It will upload when the connection returns.','Endergebnis auf diesem Handy gespeichert. Es wird übertragen, sobald die Verbindung zurück ist.','Résultat final enregistré sur ce téléphone. Il sera envoyé au retour de la connexion.','Resultado final guardado en este móvil. Se enviará cuando vuelva la conexión.'):
      t('Keep scoring. Rallies are saved on this phone and will reach the watches when you reconnect.','Zähle weiter. Die Ballwechsel bleiben auf dem Handy und erreichen die Uhren, sobald du wieder online bist.','Continuez à arbitrer. Les échanges sont enregistrés ici et transmis aux montres au retour de la connexion.','Sigue arbitrando. Los puntos se guardan aquí y llegarán a los relojes al volver la conexión.');
    if(local?.final&&!dirty(active))hint=finalSent();
    if(!backgroundEnabled&&dirty(active))hint+=' '+t('Keep this page open or reopen this link later.','Lass die Seite offen oder öffne diesen Link später erneut.','Gardez cette page ouverte ou rouvrez ce lien plus tard.','Deja la página abierta o vuelve a abrir este enlace más tarde.');retry=true;
  }else if(dirty(active)){
    mode='pending';badge=t('Sending','Wird gesendet','Envoi en cours','Enviando');
    hint=t('Saved on this phone. Sending the latest score to the watches…','Auf diesem Handy gespeichert. Der aktuelle Spielstand wird an die Uhren übertragen …','Enregistré sur ce téléphone. Envoi du score aux montres…','Guardado en este móvil. Enviando el marcador a los relojes…');
  }else {
    badge=t('Synced','Synchronisiert','Synchronisé','Sincronizado');
    if(local?.final)hint=finalSent();
    else if(offlineShell)hint=t('Ready to score offline. Tap the side that wins each rally.','Bereit für Offline-Betrieb. Tippe bei jedem Ballwechsel auf die Gewinnerseite.','Prêt pour le mode hors ligne. Touchez le camp qui gagne chaque échange.','Listo para arbitrar sin conexión. Toca el lado que gana cada punto.');
    else hint=t('Tap the rally winner. Keep this page open to score if the connection drops.','Tippe auf den Gewinner des Ballwechsels. Lass die Seite zum Zählen ohne Verbindung offen.','Touchez le gagnant de l’échange. Gardez cette page ouverte pour arbitrer en cas de coupure.','Toca el lado que gana el punto. Deja esta página abierta para arbitrar si se pierde la conexión.');
  }
  if(el('status').textContent!==hint)el('status').textContent=hint;
  if(el('connection').textContent!==(badge||''))el('connection').textContent=badge||'';
  el('connection').hidden=!badge;el('connection').dataset.mode=mode;
  el('retry').hidden=!retry;
}
function finalSent(){return t('Final result sent. Connected watch users can now save it to their results.','Endergebnis übertragen. Die verbundenen Uhren können es jetzt in ihre Ergebnisse übernehmen.','Résultat final transmis. Les utilisateurs connectés peuvent l’enregistrer sur leur montre.','Resultado final enviado. Quienes estén conectados pueden guardarlo en su reloj.');}
function option(select,value,label){const o=document.createElement('option');o.value=String(value);o.textContent=label;select.append(o);}
function refreshReceiver(prefer){
  if(!match)return;
  const first=Number(el('first').value),max=match.format.doubles?4:2;
  el('other').replaceChildren();
  for(let i=0;i<max;i++)if(i%2!==first%2)option(el('other'),i,slot(i));
  if(prefer!=null&&[...el('other').options].some(o=>Number(o.value)===prefer))el('other').value=String(prefer);
}
function summary(m) {return m.sets.join('–')+' · '+m.games.map(g=>g.score.join('–')+(g.format.nested&&!g.tie&&!g.entered&&g.winner<0?' ('+g.display(0)+'–'+g.display(1)+')':'')).join(', ');}
function render(){
  stateCopy();el('board').hidden=!match;el('match-sheet').hidden=!match;
  if(!match||!active)return;
  const m=match, g=m.game, f=m.format, blocked=!!(active.conflict||active.unavailable||storageFailed), writable=!local.final&&!blocked;
  const single=t('Singles','Einzel','Simple','Individual'),double=t('Doubles','Doppel','Double','Dobles');
  const sport=t(...sports[f.sport]),count=m.games.length;
  el('format').textContent=sport+' · '+(f.doubles?double:single);
  el('period').textContent=g.tie?t('Match tie-break','Match-Tiebreak','Super jeu décisif','Supertiebreak'):f.nested?t('Set {0}','Satz {0}','Set {0}','Set {0}',count):t('Game {0}','Satz {0}','Manche {0}','Juego {0}',count);
  el('rule').textContent=t('Best of {0}','Best of {0}','Au meilleur des {0}','Al mejor de {0}',m.best);
  if(!f.nested)el('rule').textContent+=' · '+t('to {0}','bis {0}','à {0}','a {0}',f.target);
  else if(g.tie||g.tieBreak)el('rule').textContent+=' · '+t('to {0}','bis {0}','à {0}','a {0}',g.tie?10:7);
  const server=m.server, needServer=g.pickle&&g.first<0;
  for(const side of [0,1]){
    const label=sideName(side);el('name-'+side).textContent=label;
    const points=String(g.display(side));
    el('point-'+side).textContent=points;
    el('point-'+side).dataset.length=String(points.length);
    const scoring=writable&&g.winner<0&&m.winner<0&&!needServer;
    el('rally-'+side).textContent=scoring?t('Rally won  +','Ballwechsel gewonnen  +','Échange gagné  +','Punto ganado  +'):'';
    const b=el('side-'+side);b.disabled=!scoring;b.classList.toggle('next',server>=0&&server%2===side);
    b.setAttribute('aria-label',scoring?t('Rally won by {0}, {1}','Ballwechsel für {0}, {1}','Échange gagné par {0}, {1}','Punto ganado por {0}, {1}',label,points):label+' · '+points);
  }
  el('serve').textContent='';
  el('choose-starters').hidden=!(needServer&&m.winner<0&&writable);
  if(needServer&&m.winner<0)el('serve').textContent=t('Choose the starting players before the first rally.','Wähle vor dem ersten Ballwechsel die Startspieler.','Choisissez les premiers joueurs avant le premier échange.','Elige a los jugadores iniciales antes del primer punto.');
  else if(server>=0){
    const name=server<4?slot(server):sideName(server%2);
    el('serve').textContent=t('Serving · {0}','Aufschlag · {0}','Au service · {0}','Al saque · {0}',name);
    if(g.pickle){
      const side=server%2,call=[g.score[side],g.score[1-side]];
      if(f.deuce==='side-out'&&f.doubles)call.push(g.pNumber);
      const right=f.doubles?server===g.right[side]:g.score[side]%2===0;
      el('serve').textContent+=' · '+call.join('–')+' · '+(right?t('right','rechts','à droite','derecha'):t('left','links','à gauche','izquierda'));
    }
  }
  const wins=m.sets.join('–');
  el('sets').textContent=f.nested?t('Sets {0}','Sätze {0}','Sets {0}','Sets {0}',wins):t('Games {0}','Sätze {0}','Manches {0}','Juegos {0}',wins);
  const unit=f.nested?t('Set','Satz','Set','Set'):t('Game','Satz','Manche','Juego');
  renderScorecard(el('scorecard'),unit,[sideName(0),sideName(1)],m.games.map((game,index)=>{
    const current=index===count-1&&game.winner<0&&m.winner<0;
    return {
      label:game.tie?t('Match tie-break','Match-Tiebreak','Super jeu décisif','Supertiebreak'):unit+' '+(index+1),
      score:game.score,current,
      note:current?t('Current','Aktuell','En cours','Actual'):m.retired>=0&&game.winner<0?t('Unfinished','Unvollständig','Inachevé','Sin terminar'):''
    };
  }));
  const finish=m.winner>=0;
  el('outcome').hidden=!finish;
  if(finish){
    let message=t('{0} wins','{0} gewinnt','Victoire de {0}','Gana {0}',sideName(m.winner));
    if(m.retired>=0)message+=' · '+t('retirement','Aufgabe','abandon','retirada');
    el('outcome').textContent=message;
  }
  el('undo').hidden=local.final;el('undo').disabled=!writable||!m.canUndo;
  el('advance').hidden=!writable||!m.nextReady;
  el('advance').textContent=f.nested?t('Start next set','Nächsten Satz beginnen','Commencer le set suivant','Empezar el siguiente set'):t('Start next game','Nächsten Satz beginnen','Commencer la manche suivante','Empezar el siguiente juego');
  el('finalize').hidden=!finish||!writable;
  const setup=el('server-settings');setup.hidden=!writable||finish||g.winner>=0||g.history.length>0;
  if(!setup.hidden){
    if(needServer)setup.open=true;
    const first=el('first'),associated=g.first>=0?g.first:0;
    const setupKey=local.match+'|'+document.documentElement.lang;
    if(first.dataset.state!==setupKey){
      first.replaceChildren();for(let i=0;i<(f.doubles?4:2);i++)option(first,i,slot(i));
      first.value=String(associated);refreshReceiver(g.other);first.dataset.state=setupKey;
    }
  }
  el('retirement').hidden=!writable||finish;
  const previous=el('retire-side').value;el('retire-side').replaceChildren();
  for(const side of [0,1])option(el('retire-side'),side,sideName(side));
  if(previous)el('retire-side').value=previous;
  el('conflict').hidden=!active.conflict||!remote;
  if(active.conflict&&remote){
    el('local-score').textContent=t('This phone','Dieses Handy','Ce téléphone','Este móvil')+'\n'+summary(m);
    el('remote-score').textContent=t('Synced score','Synchronisierter Stand','Score synchronisé','Marcador sincronizado')+'\n'+summary(remote);
    el('keep-local').hidden=!!active.conflict.final;
  }
}
async function readLatest(){
  if(!link||!key)return;
  const read=++readNumber,record=await storage.read(link.id);
  if(!record){if(read===readNumber){active=null;local=null;match=null;remote=null;render();}return;}
  const data=await openSnapshot(key,link.id,record.cipher), decoded=readSnapshot(data);
  if(record.final!==data.final)throw Error('Invalid room');
  let conflict;
  if(record.conflict)conflict=readSnapshot(await openSnapshot(key,link.id,record.conflict.cipher));
  if(read!==readNumber)return;
  active=record;local=data;match=decoded;remote=conflict;render();
}
function notify(){channel?.postMessage('update');}
function schedule(delay){
  clearTimeout(timer);timer=setTimeout(()=>{if(!document.hidden)sync();},delay);
}
async function sync(){
  if(!link||!key||busy||storageFailed)return;
  busy=(async()=>{
    let loaded=false;
    try{
      let record=await storage.read(link.id);
      if(!record){
        const initial=await api.get(),data=await openSnapshot(key,link.id,initial.cipher);readSnapshot(data);
        if(data.final!==initial.final)throw Error('Invalid room');
        record=await storage.change(link.id,r=>r?undefined:acceptRemote(link.id,initial));
      }else record=await synchronize(storage,link.id,api);
      connection='synced';failedToOpen=false;backoff=2000;loaded=true;
      await readLatest();notify();
      if(record&&!record.wrappedToken&&!record.final&&registration&&'sync' in registration){
        try{await rememberUploadToken(link.id,link.token);backgroundEnabled=true;}catch{backgroundEnabled=false;}
      }
    }catch(error){
      failedToOpen=true;
      if(isClosed(error)){
        connection='closed';
        await storage.change(link.id,r=>r?(dirty(r)?{...r,unavailable:true}:null):undefined).catch(()=>{});
        await readLatest().catch(()=>{});
      }else {connection='offline';backoff=Math.min(backoff*2,30000);}
      if(!active&&online()&&!isClosed(error)&&error.name==='OperationError')
        errorText(t('This link could not unlock the match. Scan the referee QR again.','Dieser Link kann die Partie nicht öffnen. Scanne den Schiedsrichter-QR erneut.','Ce lien ne déverrouille pas le match. Scannez à nouveau le QR d’arbitrage.','Este enlace no puede abrir el partido. Escanea de nuevo el QR de arbitraje.'));
      render();
    }finally{
      busy=null;
      if(!document.hidden&&connection!=='closed'&&!active?.unavailable){
        if(!loaded||active&&dirty(active)&&!active.conflict)schedule(loaded?250:backoff);
        else if(!active?.final)schedule(18000);
      }
    }
  })();
  return busy;
}
function registerRetry(){
  if(!registration||!backgroundEnabled)return;
  registration.sync.register('ks-referee-'+link.id).catch(()=>{backgroundEnabled=false;if(active)render();});
}
function dispatch(action) {
  if(!active||!match||active.conflict||active.unavailable||storageFailed||local.final)return;
  const expected=local.match;
  let question;
  if(action.type==='retire')question=t('Record the retirement of {0}?','Aufgabe von {0} erfassen?','Enregistrer l’abandon de {0} ?','¿Registrar la retirada de {0}?',sideName(action.side));
  if(action.type==='finish')question=t('Confirm this final result for all connected watches?','Dieses Endergebnis für alle verbundenen Uhren bestätigen?','Confirmer ce résultat final pour toutes les montres connectées ?','¿Confirmar este resultado final para todos los relojes conectados?');
  if(action.type==='undo'&&match.undoEntered)question=t('Remove the entire manually entered score?','Den gesamten manuell erfassten Satz zurücknehmen?','Retirer toute la manche saisie manuellement ?','¿Eliminar todo el juego introducido manualmente?');
  if(question&&!window.confirm(question))return;
  queue=queue.then(async()=>{
    const next=await updateLocal(storage,link.id,async record=>{
      const old=await openSnapshot(key,link.id,record.cipher);
      if(question&&old.match!==expected)return null;
      const changed=applyAction(old,action);if(!changed)return null;
      return {cipher:await sealSnapshot(key,link.id,changed),final:changed.final};
    });
    await readLatest();
    if(!next){errorText(t('The score changed. Check it before trying again.','Der Spielstand hat sich geändert. Prüfe ihn vor dem nächsten Versuch.','Le score a changé. Vérifiez-le avant de réessayer.','El marcador ha cambiado. Revísalo antes de volver a intentarlo.'));return;}
    errorText('');
    if(action.type==='starters')el('server-settings').open=false;
    if(action.type==='retire')el('retirement').open=false;
    notify();registerRetry();schedule(40);
  }).catch(()=>{storageFailed=true;render();});
}
async function resolve(keep) {
  if(!active?.conflict)return;
  const expected=active.conflict;
  if(!confirm(t('Use the selected score for this match?','Den gewählten Stand für diese Partie übernehmen?','Utiliser le score sélectionné pour ce match ?','¿Usar el marcador seleccionado para este partido?')))return;
  try{await resolveConflict(storage,link.id,keep,expected);await readLatest();notify();registerRetry();schedule(0);}catch{storageFailed=true;render();}
}
async function prepareOffline() {
  if(!workerSupported)return;
  try{
    const reg=await navigator.serviceWorker.register('/referee-sw.js',{scope:'/referee',type:'module',updateViaCache:'none'});
    registration=await navigator.serviceWorker.ready;
    offlineShell=true;
    if(link&&'sync' in reg&&active&&!active.final){
      try{await rememberUploadToken(link.id,link.token);backgroundEnabled=true;if(dirty(active))registerRetry();}catch{backgroundEnabled=false;}
    }
    render();
  }catch{offlineShell=false;render();}
}
for(const side of [0,1])el('side-'+side).onclick=()=>dispatch({type:'score',side});
el('first').onchange=()=>refreshReceiver();
const localize=()=>{fillFixedCopy();render();};
mountLanguage(el('language'),localize);localize();
if(link){
  const refresh=()=>readLatest().catch(()=>{});
  if('BroadcastChannel' in globalThis){channel=new BroadcastChannel('ks-referee-'+link.id);channel.onmessage=refresh;}
  navigator.serviceWorker?.addEventListener('message',e=>{if(e.data?.type==='referee-update'&&e.data.id===link.id)refresh();});
  addEventListener('online',()=>{connection='loading';schedule(0);});
  addEventListener('offline',()=>{connection='offline';render();registerRetry();});
  document.addEventListener('visibilitychange',()=>{if(!document.hidden){refresh();schedule(0);}else registerRetry();});
  prepareOffline();
  try{
    key=await roomKey(link.key);api=transport(link.id,link.token);
    await storage.change(link.id,r=>r&&!dirty(r)&&r.expiresAt<=Date.now()?null:undefined);
    clearExpiredCopies(link.id).catch(()=>{});
    try {await readLatest();}catch {errorText(t('Scan the original referee QR to reopen this match.','Scanne den ursprünglichen Schiedsrichter-QR, um die Partie zu öffnen.','Scannez le QR d’arbitrage d’origine pour rouvrir le match.','Escanea el QR de arbitraje original para reabrir el partido.')); }
    if(active?.wrappedToken&&registration&&'sync' in registration)backgroundEnabled=true;
    await sync();
  }catch{storageFailed=true;render();}
}
