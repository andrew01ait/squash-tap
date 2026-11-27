import {resolveLanguage} from './locale.mjs';

const copy = {
  en: {
    title: 'Watch invitation', label: 'Watch code', copy: 'Copy code', copied: 'Code copied.',
    manual: 'Select the code above to copy it.',
    instructions: 'On each Wear OS watch, open Keep Score → Live sharing & referee → Join with watch code. Enter this code to follow the score.',
    unavailable: 'Scan the invitation again or ask the host to show the watch code.'
  },
  de: {
    title: 'Einladung für deine Uhr', label: 'Uhrencode', copy: 'Code kopieren', copied: 'Code kopiert.',
    manual: 'Markiere den Code oben, um ihn zu kopieren.',
    instructions: 'Öffne auf jeder Wear-OS-Uhr Keep Score → Live teilen & Schiedsrichter → Mit Uhrencode beitreten. Gib den Code ein, um den Spielstand zu verfolgen.',
    unavailable: 'Scanne die Einladung erneut oder lass dir den Uhrencode auf der Gastgeberuhr anzeigen.'
  },
  fr: {
    title: 'Invitation pour ta montre', label: 'Code montre', copy: 'Copier le code', copied: 'Code copié.',
    manual: 'Sélectionne le code ci-dessus pour le copier.',
    instructions: 'Sur chaque montre Wear OS, ouvre Keep Score → Direct et arbitrage → Rejoindre avec un code. Saisis ce code pour suivre le score.',
    unavailable: 'Scanne de nouveau l’invitation ou demande à l’hôte d’afficher le code.'
  },
  es: {
    title: 'Invitación para tu reloj', label: 'Código del reloj', copy: 'Copiar código', copied: 'Código copiado.',
    manual: 'Selecciona el código de arriba para copiarlo.',
    instructions: 'En cada reloj Wear OS, abre Keep Score → Directo y arbitraje → Unirse con código. Introduce el código para seguir el marcador.',
    unavailable: 'Escanea la invitación de nuevo o pide al anfitrión que muestre el código.'
  }
};

const lang = resolveLanguage(new URLSearchParams(location.search).get('lang'), navigator.languages);
const labels = copy[lang];
const byId = id => document.getElementById(id);
document.documentElement.lang = lang;
document.title = labels.title + ' · Keep Score';
byId('title').textContent = labels.title;

const value = new URLSearchParams(location.hash.slice(1)).get('code') || '';
if (/^[0-9A-HJKMNP-TV-Z]{10}$/.test(value)) {
  const code = value.slice(0, 5) + ' ' + value.slice(5);
  byId('label').textContent = labels.label;
  byId('code').textContent = code;
  byId('instructions').textContent = labels.instructions;
  byId('copy').textContent = labels.copy;
  byId('copy').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(code);
      byId('status').textContent = labels.copied;
    } catch {
      byId('status').textContent = labels.manual;
    }
  });
  byId('invite').hidden = false;
} else {
  byId('unavailable').textContent = labels.unavailable;
  byId('unavailable').hidden = false;
}
