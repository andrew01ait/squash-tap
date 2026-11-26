const copy = {
  en: {
    title: 'Imprint & contact', home: 'Back to Keep Score', author: 'Site owner and developer', residence: 'Vienna, Austria',
    purpose: 'Development and distribution of Keep Score, a Wear OS scorekeeper for racket sports.',
    about: 'About me', bio: 'I play squash and created Keep Score to keep track of rallies on my watch. You can now score six racket sports with it.',
    contact: 'Contact', show: 'Show email and phone', email: 'Email', phone: 'Phone',
    hint: 'Reveal the contact details to send an email or make a call.', unavailable: 'Contact details are temporarily unavailable. Please try again later.',
    supportHint: 'To ask about supporting Keep Score, reveal my email address below.', supportSubject: 'Supporting Keep Score',
    language: 'Language'
  },
  de: {
    title: 'Impressum & Kontakt', home: 'Zur Keep-Score-Startseite', author: 'Medieninhaber und Entwickler', residence: 'Wien, Österreich',
    purpose: 'Entwicklung und Bereitstellung von Keep Score, einer Wear-OS-App zum Punktezählen bei Rückschlagsportarten.',
    about: 'Über mich', bio: 'Ich spiele Squash und habe Keep Score entwickelt, um die Punkte auf meiner Uhr mitzuzählen. Heute kannst du damit bei sechs Rückschlagsportarten den Spielstand festhalten.',
    contact: 'Kontakt', show: 'E-Mail und Telefonnummer anzeigen', email: 'E-Mail', phone: 'Telefon',
    hint: 'Blende die Kontaktdaten ein, um eine E-Mail zu schreiben oder anzurufen.', unavailable: 'Die Kontaktdaten sind vorübergehend nicht verfügbar. Bitte versuche es später erneut.',
    supportHint: 'Du möchtest Keep Score unterstützen? Blende meine E-Mail-Adresse ein und schreib mir.', supportSubject: 'Keep Score unterstützen',
    language: 'Sprache'
  },
  fr: {
    title: 'Mentions légales et contact', home: 'Retour à Keep Score', author: 'Éditeur et développeur', residence: 'Vienne, Autriche',
    purpose: 'Développement et distribution de Keep Score, un compteur de points pour les sports de raquette sur Wear OS.',
    about: 'À propos de moi', bio: 'Je joue au squash et j’ai créé Keep Score pour compter les points sur ma montre. Tu peux aujourd’hui suivre le score de six sports de raquette.',
    contact: 'Contact', show: 'Afficher l’e-mail et le téléphone', email: 'E-mail', phone: 'Téléphone',
    hint: 'Affiche les coordonnées pour envoyer un e-mail ou passer un appel.', unavailable: 'Les coordonnées sont temporairement indisponibles. Réessaie plus tard.',
    supportHint: 'Pour contribuer à Keep Score, affiche mon adresse e-mail et écris-moi.', supportSubject: 'Soutenir Keep Score',
    language: 'Langue'
  },
  es: {
    title: 'Aviso legal y contacto', home: 'Volver a Keep Score', author: 'Titular y desarrollador', residence: 'Viena, Austria',
    purpose: 'Desarrollo y distribución de Keep Score, un marcador para deportes de raqueta en Wear OS.',
    about: 'Sobre mí', bio: 'Juego al squash y creé Keep Score para llevar la cuenta de los puntos en mi reloj. Ahora puedes usarla para seis deportes de raqueta.',
    contact: 'Contacto', show: 'Mostrar correo y teléfono', email: 'Correo electrónico', phone: 'Teléfono',
    hint: 'Muestra los datos de contacto para escribir un correo o llamar.', unavailable: 'Los datos de contacto no están disponibles temporalmente. Inténtalo más tarde.',
    supportHint: 'Si quieres apoyar a Keep Score, muestra mi correo electrónico y escríbeme.', supportSubject: 'Apoyar a Keep Score',
    language: 'Idioma'
  }
};

const names = {en: 'English', de: 'Deutsch', fr: 'Français', es: 'Español'};

function pageLanguage(request, url) {
  const chosen = url.searchParams.get('lang');
  if (Object.hasOwn(copy, chosen)) return chosen;
  const ranges = (request.headers.get('Accept-Language') || '').split(',').map(range => {
    const [tag, weight = 'q=1'] = range.trim().toLowerCase().split(/\s*;\s*/);
    return {code: tag.split('-')[0], weight: Number(weight.replace(/^q=/, ''))};
  });
  return ranges.filter(entry => entry.weight > 0 && Object.hasOwn(copy, entry.code))
    .sort((a, b) => b.weight - a.weight)[0]?.code || 'en';
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
}

export function handleSupport(request, env) {
  const url = new URL(request.url);
  const lang = pageLanguage(request, url);
  const headers = {'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Robots-Tag': 'noindex, nofollow'};
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return new Response(null, {status: 405, headers: {...headers, Allow: 'GET, HEAD'}});
  }
  let destination = new URL('/?lang=' + lang + '#support', url);
  if (env.SUPPORT_URL) {
    try {
      const provider = new URL(env.SUPPORT_URL);
      if (provider.protocol === 'https:' && !provider.username && !provider.password && provider.origin !== url.origin) {
        destination = provider;
      }
    } catch {
      // The landing page offers both payment providers.
    }
  }
  return new Response(null, {status: 302, headers: {...headers, Location: destination.href}});
}

export async function handleImprint(request, env) {
  const url = new URL(request.url);
  const lang = pageLanguage(request, url);
  const l = copy[lang];
  const support = url.searchParams.get('topic') === 'support';
  const topic = support ? '&amp;topic=support' : '';
  const hint = support ? l.supportHint : l.hint;
  const headers = {
    'Content-Type': 'text/html; charset=utf-8', 'Content-Language': lang, 'Cache-Control': 'private, no-store',
    'Vary': 'Accept-Language', 'X-Robots-Tag': 'noindex, nofollow, nosnippet', 'Referrer-Policy': 'same-origin',
    'X-Content-Type-Options': 'nosniff',
    'Content-Security-Policy': "default-src 'none'; style-src 'self'; img-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'"
  };
  if (!['GET', 'HEAD', 'POST'].includes(request.method)) return new Response(null, {status: 405, headers: {...headers, Allow: 'GET, HEAD, POST'}});
  const reveal = request.method === 'POST';
  if (reveal) {
    const origin = request.headers.get('Origin');
    if (request.headers.get('Sec-Fetch-Site') === 'cross-site' || (origin && origin !== url.origin)) return new Response(null, {status: 403, headers});
    // A same-origin form POST reveals the public details. No visitor input is read or stored.
  }
  const email = reveal && env.IMPRINT_EMAIL;
  const phone = reveal && env.IMPRINT_PHONE;
  const available = Boolean(email && phone && /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/.test(email) && /^\+\d{6,15}$/.test(phone));
  const status = reveal && !available ? 503 : 200;
  if (status === 503) headers['Retry-After'] = '60';
  const contact = available
    ? `<dl class="contacts"><div><dt>${l.email}</dt><dd><a href="mailto:${escapeHtml(email)}${support ? '?subject=' + encodeURIComponent(l.supportSubject) : ''}">${escapeHtml(email)}</a></dd></div><div><dt>${l.phone}</dt><dd><a href="tel:${escapeHtml(phone)}">${escapeHtml(phone)}</a></dd></div></dl>`
    : `<p>${reveal ? l.unavailable : hint}</p><form method="post" action="/imprint?lang=${lang}${topic}#contact"><button type="submit">${l.show}</button></form>`;
  const languages = Object.entries(names).map(([code,name]) =>
    `<a href="/imprint?lang=${code}${topic}" hreflang="${code}" lang="${code}"${code === lang ? ' aria-current="page"' : ''}>${name}</a>`).join('');
  const html = `<!doctype html><html lang="${lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>${escapeHtml(l.title)} · Keep Score</title><meta name="robots" content="noindex,nofollow,nosnippet"><meta name="theme-color" content="#080d17">
    <link rel="icon" href="/favicon.svg"><link rel="stylesheet" href="/imprint.css"></head><body>
    <header><a class="brand" href="/"><img src="/favicon.svg" alt="" width="36" height="36">Keep Score</a><nav aria-label="${l.language}">${languages}</nav></header>
    <main><h1>${escapeHtml(l.title)}</h1><section><h2>${l.author}</h2><p class="owner">Andrew Lindley</p><p>${l.residence}</p><p>${l.purpose}</p></section>
    <section id="contact"><h2>${l.contact}</h2>${contact}</section>
    <section><h2>${l.about}</h2><p>${l.bio}</p></section><footer><a href="/">${l.home}</a></footer></main></body></html>`;
  return new Response(request.method === 'HEAD' ? null : html, {status, headers});
}
