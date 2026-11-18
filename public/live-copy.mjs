import {t} from './locale.mjs';
import {rowRules} from './sports.mjs?v=0.16.3';

export const liveCopy = {
  "Me": ["Ich","Moi","Yo"],
  "Opponent": ["Gegner","Adversaire","Rival"],
  "Opponent 1": ["Gegner 1","Adversaire 1","Rival 1"],
  "Opponent 2": ["Gegner 2","Adversaire 2","Rival 2"],
  "Partner": ["Partner","Partenaire","Pareja"],
  "Our team": ["Unser Team","Notre équipe","Nuestro equipo"],
  "Opposing team": ["Gegnerteam","Équipe adverse","Equipo rival"],
  "Keep Score — Live match": ["Keep Score – Live-Partie","Keep Score — Match en direct","Keep Score — Partido en directo"],
  "LIVE MATCH": ["LIVE-PARTIE","MATCH EN DIRECT","PARTIDO EN DIRECTO"],
  "Open an audience link shared from a watch.": ["Öffne den Zuschauerlink von der Uhr, die die Partie überträgt.","Ouvre le lien partagé par la montre qui diffuse le match.","Abre el enlace para espectadores que comparte el reloj del partido."],
  "BROADCAST CLOSED": ["ÜBERTRAGUNG BEENDET","DIFFUSION TERMINÉE","RETRANSMISIÓN CERRADA"],
  "Final match result": ["Endergebnis der Partie","Résultat final du match","Resultado final del partido"],
  "FINAL RESULT": ["ENDERGEBNIS","RÉSULTAT FINAL","RESULTADO FINAL"],
  "Serving": ["Aufschlag","Au service","Al saque"],
  "Game and set scores": ["Spiel- und Satzstände","Scores des jeux et des manches","Marcadores de juegos y sets"],
  "PREVIOUS MEETINGS": ["FRÜHERE BEGEGNUNGEN","RENCONTRES PRÉCÉDENTES","ENCUENTROS ANTERIORES"],
  "Head to head": ["Direkter Vergleich","Face-à-face","Balance directo"],
  "Last meeting": ["Letztes Duell","Dernière rencontre","Último encuentro"],
  "Colours": ["Farben","Couleurs","Colores"],
  "Scoreboard colour scheme": ["Farbschema der Anzeige","Couleurs du tableau","Colores del marcador"],
  "Classic": ["Klassisch","Classique","Clásico"],
  "Neon Lime": ["Neongrün","Vert néon","Verde neón"],
  "Sunlight": ["Sonnenlicht","Plein soleil","Luz solar"],
  "Copy audience link": ["Zuschauerlink kopieren","Copier le lien du public","Copiar enlace para espectadores"],
  "Share audience link": ["Zuschauerlink teilen","Partager le lien du public","Compartir enlace para espectadores"],
  "Fullscreen": ["Vollbild","Plein écran","Pantalla completa"],
  "Read-only scoreboard. Updates arrive after scoring on the watch. If its connection drops, the last received score stays visible. Anyone with the complete audience link can view this match and any previous-meeting summary. Final results remain available for 10 minutes after completion; stopping sharing closes the link sooner.": ["Hier kannst du den Spielstand mitlesen. Nach jedem Eintrag überträgt die Uhr den neuen Stand. Bei einem Verbindungsabbruch bleibt der zuletzt empfangene Stand sichtbar. Jeder mit dem vollständigen Zuschauerlink sieht die Partie und den freigegebenen direkten Vergleich. Das Endergebnis bleibt zehn Minuten sichtbar. Die Freigabe kann früher beendet werden.","Ce tableau est en lecture seule. La montre transmet le score après chaque saisie. En cas de coupure, le dernier score reçu reste affiché. Toute personne disposant du lien complet peut voir le match et le bilan des rencontres précédentes. Le résultat final est visible dix minutes, sauf si le partage est arrêté avant.","Este marcador es de solo lectura. Se actualiza al puntuar en el reloj. Si se pierde la conexión, sigue visible el último marcador recibido. Quien tenga el enlace completo puede ver el partido y el balance de encuentros anteriores. El resultado final se muestra durante diez minutos, salvo que el emisor detenga antes la retransmisión."],
  "Ended": ["Beendet","Terminé","Finalizado"],
  "Ready to play": ["Spielbereit","Prêt à jouer","Listo para jugar"],
  "Playing": ["Läuft","En cours","En juego"],
  "Paused": ["Pausiert","En pause","En pausa"],
  "Between games": ["Satzpause","Pause entre les manches","Descanso"],
  "Finished": ["Beendet","Terminé","Finalizado"],
  "Final · {0} min left": ["Endstand · noch {0} Min.","Terminé · encore {0} min","Final · quedan {0} min"],
  "{0} wins": ["{0} gewinnt","Victoire de {0}","Victoria de {0}"],
  "By retirement · actual scores shown below": ["Durch Aufgabe · unten steht der erspielte Stand","Sur abandon · les scores réellement joués figurent ci-dessous","Por abandono · se muestran los marcadores reales"],
  "Best of {0} · {1}": ["Best of {0} · {1}","Au meilleur de {0} · {1}","Al mejor de {0} · {1}"],
  "Serving · {0}": ["Aufschlag · {0}","Au service · {0}","Al saque · {0}"],
  "Final sets": ["Satz-Endstand","Manches finales","Sets finales"],
  "Final games": ["Endstand nach Sätzen","Jeux gagnés","Juegos finales"],
  "Points · sets {0}": ["Punkte · Sätze {0}","Points · manches {0}","Puntos · sets {0}"],
  "Points · games {0}": ["Punkte · Sätze {0}","Points · jeux {0}","Puntos · juegos {0}"],
  "Serve call: {0}": ["Aufschlagansage: {0}","Annonce du score : {0}","Canto del saque: {0}"],
  "Match tie-break": ["Match-Tie-Break","Super jeu décisif","Desempate de partido"],
  "Set": ["Satz","Manche","Set"],
  "Game": ["Satz","Jeu","Juego"],
  "sets": ["Sätze","manches","sets"],
  "games": ["Sätze","jeux","juegos"],
  " · unfinished": [" · unvollständig"," · en cours à l’arrêt"," · sin terminar"],
  "Last score update {0}. Updates arrive after a rally or match action.": ["Letzte Aktualisierung um {0}. Neue Stände kommen nach einem Ballwechsel oder einer Spielaktion.","Dernière mise à jour à {0}. Le score s’actualise après un échange ou une action dans le match.","Última actualización a las {0}. El marcador cambia después de una jugada o de una acción del partido."],
  "One previous meeting": ["Eine frühere Begegnung","Une rencontre précédente","Un encuentro anterior"],
  "{0} previous meetings": ["{0} frühere Begegnungen","{0} rencontres précédentes","{0} encuentros anteriores"],
  "same teams": ["gleiche Teams","mêmes équipes","mismos equipos"],
  "same opponent": ["gleicher Gegner","même adversaire","mismo rival"],
  "before this match": ["vor dieser Partie","avant ce match","antes de este partido"],
  "{0} won": ["{0} hat gewonnen","Victoire de {0}","Victoria de {0}"],
  "{0} won by retirement": ["{0} hat durch Aufgabe gewonnen","Victoire de {0} sur abandon","Victoria de {0} por abandono"],
  "Final result available until {0} · {1} min remaining": ["Endergebnis bis {0} verfügbar · noch {1} Min.","Résultat final visible jusqu’à {0} · encore {1} min","Resultado final visible hasta las {0} · quedan {1} min"],
  "Broadcast ended": ["Übertragung beendet","Diffusion terminée","Retransmisión finalizada"],
  "This audience link has expired. Final results remain available for 10 minutes after completion.": ["Dieser Zuschauerlink ist abgelaufen. Das Endergebnis ist nach Spielende zehn Minuten lang sichtbar.","Ce lien a expiré. Le résultat final reste visible dix minutes après le match.","Este enlace ha caducado. El resultado final se muestra durante diez minutos tras el partido."],
  "This match is no longer available on this audience link. Sharing has stopped or the link has expired.": ["Die Freigabe wurde beendet oder der Zuschauerlink ist abgelaufen.","Ce match n’est plus accessible. Le partage a été arrêté ou le lien a expiré.","El partido ya no está disponible. La retransmisión se ha detenido o el enlace ha caducado."],
  "Match discarded": ["Partie verworfen","Match annulé","Partido descartado"],
  "The scoring watch discarded this match. No result was saved.": ["Die zählende Uhr hat die Partie verworfen. Es wurde kein Ergebnis gespeichert.","La montre a annulé ce match. Aucun résultat n’a été enregistré.","El reloj que puntuaba ha descartado el partido. No se ha guardado ningún resultado."],
  "Match finished · final result": ["Partie beendet · Endergebnis","Match terminé · résultat final","Partido finalizado · resultado final"],
  "Connected · following this match": ["Verbunden · Live-Spielstand","Connecté · suivi en direct","Conectado · siguiendo el partido"],
  "Connection interrupted · showing the last received score": ["Verbindung unterbrochen · letzter empfangener Stand","Connexion interrompue · dernier score reçu","Conexión interrumpida · último marcador recibido"],
  "Could not connect · retrying shortly": ["Keine Verbindung · neuer Versuch in Kürze","Connexion impossible · nouvelle tentative sous peu","Sin conexión · se reintentará en breve"],
  "Audience link copied": ["Zuschauerlink kopiert","Lien du public copié","Enlace para espectadores copiado"],
  "Copy this page’s address to share the audience link.": ["Kopiere die Seitenadresse, um den Zuschauerlink zu teilen.","Copie l’adresse de cette page pour partager le lien du public.","Copia la dirección de esta página para compartir el enlace con el público."],
  "Keep Score · live match": ["Keep Score · Live-Partie","Keep Score · Match en direct","Keep Score · Partido en directo"],
  "Use Copy audience link to share.": ["Nutze „Zuschauerlink kopieren“ zum Teilen.","Utilise « Copier le lien du public » pour le partager.","Utiliza «Copiar enlace para espectadores» para compartirlo."],
  "Fullscreen is unavailable in this browser.": ["Dieser Browser unterstützt kein Vollbild.","Le plein écran est indisponible dans ce navigateur.","La pantalla completa no está disponible en este navegador."],
  "Connecting to the live match…": ["Live-Verbindung wird hergestellt …","Connexion au match en cours…","Conectando con el partido…"],
  "Invalid audience link. Scan a fresh QR code from the scoring watch.": ["Ungültiger Zuschauerlink. Scanne einen neuen QR-Code von der zählenden Uhr.","Lien du public invalide. Scanne un nouveau QR sur la montre qui compte les points.","Enlace para espectadores no válido. Escanea otro QR del reloj que puntúa."],
  "Singles": ["Einzel","Simple","Individual"],
  "Doubles": ["Doppel","Double","Dobles"],
  "Advantage": ["Vorteil","Avantage","Ventaja"],
  "No-Ad / Golden Point": ["No-Ad / Golden Point","Point décisif","Punto de oro"],
  "Star Point": ["Star Point","Star Point","Star Point"],
  "deciding match tie-break to 10": ["Match-Tie-Break bis 10 im Entscheidungssatz","super jeu décisif en 10 points","desempate a 10 en el último set"],
  "full deciding set": ["voller Entscheidungssatz","dernière manche complète","último set completo"],
  "Traditional side-out": ["Klassisches Side-out","Points au service","Puntuación tradicional"],
  "Rally 2025": ["Rally 2025","Rally 2025","Por jugada 2025"],
  "Rally 2026": ["Rally 2026","Rally 2026","Por jugada 2026"],
  "win on serve": ["Siegpunkt bei eigenem Aufschlag","point de victoire au service","punto de victoria al saque"],
  "every rally": ["jeder Ballwechsel zählt","un point à chaque échange","un punto por jugada"],
  "to {0}, win by two": ["bis {0}, zwei Punkte Vorsprung","en {0} points, deux points d’écart","a {0}, con dos de diferencia"],
  "to {0}, win by two, cap {1}": ["bis {0}, zwei Punkte Vorsprung, maximal {1}","en {0} points, deux d’écart, plafond à {1}","a {0}, con dos de diferencia, tope de {1}"],
  "Table tennis": ["Tischtennis","Tennis de table","Tenis de mesa"],
  "Tennis": ["Tennis","Tennis","Tenis"],
  "Padel": ["Padel","Padel","Pádel"],
  "Badminton": ["Badminton","Badminton","Bádminton"],
  "Squash": ["Squash","Squash","Squash"],
  "Pickleball": ["Pickleball","Pickleball","Pickleball"],
};

export function label(text, ...values) {
  if (!Object.hasOwn(liveCopy, text)) return text;
  return t(text, ...liveCopy[text], ...values);
}

/** Older watches send a rule description made up of stable English phrases. */
export function ruleLabel(description) {
  return description.split(' · ').map(part => {
    const target = /^to (\d+), win by two(?:, cap (\d+))?$/.exec(part);
    if (target?.[2]) return label('to {0}, win by two, cap {1}', target[1], target[2]);
    if (target) return label('to {0}, win by two', target[1]);
    return label(part);
  }).join(' · ');
}


/** A saved name is always literal, even when it happens to equal an English UI label. */
export function audienceNames(score) {
  if (!score.labels) return {teams: score.names, serving: score.serveName};
  const doubles = score.mode === 'doubles';
  const placeholders = ['Me', doubles ? 'Opponent 1' : 'Opponent', 'Partner', 'Opponent 2'];
  const players = score.labels.map((name, slot) => name || label(placeholders[slot]));
  const teams = doubles ? [players[0]+' + '+players[2], players[1]+' + '+players[3]] : players.slice(0,2);
  const serving = score.server < 4 ? players[score.server] || ''
    : label(score.server === 4 ? 'Our team' : 'Opposing team');
  return {teams, serving};
}

export function scoreRules(score) {
  if (!score.format) return ruleLabel(score.rules);
  const f = rowRules({0:'3',24:score.sport,25:score.mode,26:score.format});
  const parts = [label(f.doubles ? 'Doubles' : 'Singles')];
  if (f.nested) {
    const deuce = {'advantage':'Advantage','no-ad':'No-Ad / Golden Point','star':'Star Point'};
    parts.push(label(deuce[f.deuce]), label(f.finalTie ? 'deciding match tie-break to 10' : 'full deciding set'));
  } else {
    if (f.sport === 'pickleball') {
      if (f.deuce === 'side-out') parts.push(label('Traditional side-out'));
      else if (f.deuce === 'rally-2025') parts.push(label('Rally 2025'),label('win on serve'));
      else parts.push(label('Rally 2026'),label('every rally'));
    }
    parts.push(f.sport === 'badminton'
      ? label('to {0}, win by two, cap {1}',f.target,f.target === 21 ? 30 : 21)
      : label('to {0}, win by two',f.target));
  }
  return parts.join(' · ');
}
