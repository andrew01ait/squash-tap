import {mountLanguage, t} from './locale.mjs';
import {bindPageCopy} from './page-copy.mjs';

const copy = {
  "Keep Score diagnostics": ["Keep-Score-Diagnose","Diagnostic de Keep Score","Diagnóstico de Keep Score"],
  "Export watch diagnostics": ["Uhrendiagnose exportieren","Exporter le diagnostic de la montre","Exportar el diagnóstico del reloj"],
  "Reading the diagnostic QR code…": ["Diagnose-QR wird gelesen …","Lecture du QR de diagnostic…","Leyendo el QR de diagnóstico…"],
  "Save text file": ["Textdatei speichern","Enregistrer le fichier texte","Guardar archivo de texto"],
  "Share file": ["Datei teilen","Partager le fichier","Compartir archivo"],
  "Copy text": ["Text kopieren","Copier le texte","Copiar texto"],
  "The report is processed on your phone and is not uploaded automatically. Review it before sharing.": ["Dein Handy verarbeitet den Bericht lokal. Es lädt ihn nicht automatisch hoch. Prüfe den Inhalt vor dem Teilen.","Le rapport est traité sur ton téléphone, sans envoi automatique. Vérifie son contenu avant de le partager.","El informe se procesa en tu móvil y no se sube automáticamente. Revísalo antes de compartirlo."],
  "No report loaded.": ["Kein Bericht geladen.","Aucun rapport chargé.","No se ha cargado ningún informe."],
  "Back to Keep Score": ["Zurück zu Keep Score","Retour à Keep Score","Volver a Keep Score"],
  "On your watch open MENU → Settings → Diagnostics → Send to phone, then scan its QR code.": ["Öffne auf der Uhr „Menü → Einstellungen → Diagnose → An Handy senden“. Scanne dann den QR-Code.","Sur la montre, ouvre Menu → Réglages → Diagnostic → Envoyer au téléphone, puis scanne le QR.","En el reloj, abre Menú → Ajustes → Diagnóstico → Enviar al móvil y escanea el QR."],
  "This report is too large to open.": ["Dieser Bericht ist zu groß zum Öffnen.","Ce rapport est trop volumineux.","El informe es demasiado grande."],
  "Could not read this QR. Scan the diagnostic code from the watch again.": ["Dieser QR-Code konnte nicht gelesen werden. Scanne den Diagnosecode auf der Uhr erneut.","Impossible de lire ce QR. Scanne de nouveau le code de diagnostic sur la montre.","No se ha podido leer el QR. Vuelve a escanear el código de diagnóstico del reloj."],
  "Ready. Save, copy or share the report with support.": ["Bericht bereit. Du kannst ihn speichern, kopieren oder mit dem Support teilen.","Le rapport est prêt. Enregistre-le, copie-le ou partage-le avec l’assistance.","El informe está listo. Puedes guardarlo, copiarlo o compartirlo con soporte."],
  "File sharing is unavailable here. Use Save text file to keep a copy of the report.": ["Dieser Browser kann die Datei nicht teilen. Mit „Textdatei speichern“ behältst du eine Kopie.","Le partage de fichiers est indisponible ici. Utilise « Enregistrer le fichier texte » pour conserver une copie.","Este navegador no permite compartir el archivo. Utiliza «Guardar archivo de texto» para conservar una copia."],
  "Report copied. You can paste it into a support message.": ["Bericht kopiert. Du kannst ihn in eine Nachricht an den Support einfügen.","Rapport copié. Tu peux le coller dans un message à l’assistance.","Informe copiado. Puedes pegarlo en un mensaje a soporte."],
  "Use Save text file if clipboard access is unavailable.": ["Falls die Zwischenablage gesperrt ist, wähle „Textdatei speichern“.","Si le presse-papiers est indisponible, utilise « Enregistrer le fichier texte ».","Si el portapapeles no está disponible, utiliza «Guardar archivo de texto»."],
};
const $ = id => document.getElementById(id);
const translate = key => t(key, ...copy[key]);
const staticCopy = bindPageCopy(document, copy);
let report = '', filename = '', requestNumber = 0;
let state = 'Reading the diagnostic QR code…';

function setStatus(key) {
  state = key;
  $('status').textContent = translate(key);
}
function render() {
  staticCopy();
  setStatus(state);
  $('report').textContent = report || translate('No report loaded.');
}
mountLanguage($('diagnostic-language'), render);
render();

async function loadReport() {
  const number = ++requestNumber;
  report = '';
  $('report').textContent = translate('No report loaded.');
  setStatus('Reading the diagnostic QR code…');
  for (const id of ['save','share','copy']) $(id).disabled = true;
  const payload = new URLSearchParams(location.hash.slice(1)).get('d');
  if (!payload) {
    setStatus('On your watch open MENU → Settings → Diagnostics → Send to phone, then scan its QR code.');
    return;
  }
  if (payload.length > 5000) { setStatus('This report is too large to open.'); return; }
  try {
    const raw = atob(payload.replace(/-/g,'+').replace(/_/g,'/'));
    const compressed = Uint8Array.from(raw, char => char.charCodeAt(0));
    const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream('gzip'));
    const reader = stream.getReader(), chunks = [];
    let size = 0;
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 16000) {
        await reader.cancel();
        if (number === requestNumber) setStatus('This report is too large to open.');
        return;
      }
      chunks.push(value);
    }
    const text = await new Blob(chunks).text();
    if (number !== requestNumber) return;
    report = text;
    $('report').textContent = report;
    setStatus('Ready. Save, copy or share the report with support.');
    filename = new Date().toLocaleDateString('sv-SE').replaceAll('-','') + '_KeepScore_diagnostics.txt';
    for (const id of ['save','share','copy']) $(id).disabled = false;
    history.replaceState(null, '', location.pathname);
  } catch {
    if (number === requestNumber) setStatus('Could not read this QR. Scan the diagnostic code from the watch again.');
  }
}

$('save').addEventListener('click', () => {
  const url = URL.createObjectURL(new Blob([report],{type:'text/plain;charset=utf-8'}));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
});
$('share').addEventListener('click', async () => {
  try {
    const file = new File([report], filename, {type:'text/plain'});
    if (navigator.canShare?.({files:[file]})) await navigator.share({files:[file], title:translate('Keep Score diagnostics')});
    else setStatus('File sharing is unavailable here. Use Save text file to keep a copy of the report.');
  } catch (err) {
    if (err.name !== 'AbortError') setStatus('File sharing is unavailable here. Use Save text file to keep a copy of the report.');
  }
});
$('copy').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(report);
    setStatus('Report copied. You can paste it into a support message.');
  } catch { setStatus('Use Save text file if clipboard access is unavailable.'); }
});
window.addEventListener('hashchange', loadReport);
loadReport();
