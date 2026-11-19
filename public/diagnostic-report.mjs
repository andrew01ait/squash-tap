// Schema 1 is emitted by MainActivity.diagnosticQrState(). Older links contain only the report.
const labels = {
  en: {
    flags: ['Direct install allowed', 'Notifications enabled', 'Ambient support', 'In foreground', 'Panel open', 'Scoreboard display flag', 'Display active', 'Power saver', 'Device idle', 'Battery exemption', 'Match active', 'Paused', 'Ambient mode', 'Keep screen on'],
    battery: 'Watch battery', charging: 'charging', unplugged: 'not charging', score: 'Score', sets: 'Sets', results: 'Saved results',
  },
  de: {
    flags: ['Direkte Installation erlaubt', 'Benachrichtigungen aktiviert', 'Ruhemodus unterstützt', 'Im Vordergrund', 'Ansicht geöffnet', 'Display-Flag des Spielstands', 'Display aktiv', 'Energiesparmodus', 'Gerät im Leerlauf', 'Von Akkuoptimierung ausgenommen', 'Partie aktiv', 'Pausiert', 'Ruhemodus', 'Display anlassen'],
    battery: 'Akku der Uhr', charging: 'wird geladen', unplugged: 'wird nicht geladen', score: 'Spielstand', sets: 'Sätze', results: 'Gespeicherte Ergebnisse',
  },
  fr: {
    flags: ['Installation directe autorisée', 'Notifications activées', 'Mode ambiant pris en charge', 'Au premier plan', 'Vue ouverte', 'Maintien de l’écran du score', 'Écran actif', 'Économie d’énergie', 'Montre inactive', 'Exemption de batterie', 'Match actif', 'En pause', 'Mode ambiant', 'Garder l’écran allumé'],
    battery: 'Batterie de la montre', charging: 'en charge', unplugged: 'hors charge', score: 'Score', sets: 'Manches', results: 'Résultats enregistrés',
  },
  es: {
    flags: ['Instalación directa permitida', 'Notificaciones activadas', 'Modo ambiente compatible', 'En primer plano', 'Vista abierta', 'Mantener encendido el marcador', 'Pantalla activa', 'Ahorro de energía', 'Reloj inactivo', 'Exención de batería', 'Partido activo', 'En pausa', 'Modo ambiente', 'Mantener pantalla encendida'],
    battery: 'Batería del reloj', charging: 'cargando', unplugged: 'sin cargar', score: 'Marcador', sets: 'Sets', results: 'Resultados guardados',
  },
};

export function withWatchState(report, encoded) {
  if (!encoded || encoded.length > 96) return report;
  const fields = encoded.split('.');
  const [version, language, mask, ...numbers] = fields;
  if (fields.length !== 10 || version !== '1' || !Object.hasOwn(labels, language) ||
      !/^[0-9a-z]{1,3}$/.test(mask) || !/^(?:-1|[0-9]{1,3})$/.test(numbers[0]) ||
      !numbers.slice(1).every(value => /^[0-9]{1,10}$/.test(value))) return report;
  const bits = Number.parseInt(mask, 36);
  const [battery, blue, red, blueSets, redSets, bestOf, saved] = numbers.map(Number);
  if (bits >= (1 << 15) || battery > 100) return report;
  const words = labels[language];
  const level = battery < 0 ? '—' : battery + '%';
  const charging = bits & (1 << 14) ? words.charging : words.unplugged;
  return [report, '', words.battery + ': ' + level + ' · ' + charging,
    words.score + ': ' + blue + '–' + red + ' · ' + words.sets + ' ' + blueSets + '–' + redSets,
    'Best of ' + bestOf + ' · ' + words.results + ': ' + saved,
    ...words.flags.map((label, index) => label + ': ' + Boolean(bits & (1 << index))),
  ].join('\n');
}
