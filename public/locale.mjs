/** The shared language preference contains only a locale code, never match information. */
const names = Object.freeze({en: 'English', de: 'Deutsch', fr: 'Français', es: 'Español'});
const preferenceKey = 'keep-score.language';
const supported = code => Object.hasOwn(names, code);
let preference = readPreference();

function readPreference() {
  try {
    const value = globalThis.localStorage?.getItem(preferenceKey);
    if (supported(value)) return value;
  } catch { /* Browsers can disable storage. The current tab still works. */ }
  return 'auto';
}

export function resolveLanguage(override, preferences = []) {
  if (supported(override)) return override;
  for (const value of preferences) {
    const base = String(value).toLowerCase().split(/[-_]/)[0];
    if (supported(base)) return base;
  }
  return 'en';
}

function preferredLanguages() {
  const browser = globalThis.navigator;
  return [...(browser?.languages || []), browser?.language || ''];
}

export function language() {
  return resolveLanguage(preference, preferredLanguages());
}

export function t(en, de, fr, es, ...params) {
  const text = {en, de, fr, es}[language()] ?? en;
  return text.replace(/\{(\d+)\}/g, (match, index) =>
    Number(index) < params.length ? String(params[Number(index)]) : match);
}

/** Passing a callback keeps the current page, pending actions and private session in memory. */
export function mountLanguage(element, onChange, linkLanguage) {
  if (!element) return;
  if (supported(linkLanguage)) preference = linkLanguage;
  const select = document.createElement('select');
  const label = document.createElement('label');
  const caption = document.createElement('span');
  label.className = 'language-picker';
  label.append(caption, select);
  element.replaceChildren(label);
  const automatic = document.createElement('option');
  automatic.value = 'auto';
  select.append(automatic);
  for (const [code, name] of Object.entries(names)) {
    const option = document.createElement('option');
    option.value = code;
    option.lang = code;
    option.textContent = name;
    select.append(option);
  }

  let rendered;
  function render(notify) {
    const next = language();
    document.documentElement.lang = next;
    caption.textContent = t('Language', 'Sprache', 'Langue', 'Idioma');
    automatic.textContent = t('Auto · {0}', 'Auto · {0}', 'Auto · {0}', 'Auto · {0}',
      names[resolveLanguage('auto', preferredLanguages())]);
    select.value = preference;
    const changed = next !== rendered;
    rendered = next;
    if (notify && changed) {
      if (typeof onChange === 'function') onChange(next);
      else globalThis.location.reload();
    }
  }

  select.addEventListener('change', () => {
    preference = supported(select.value) ? select.value : 'auto';
    try {
      if (preference === 'auto') localStorage.removeItem(preferenceKey);
      else localStorage.setItem(preferenceKey, preference);
    } catch { /* Private browsing may only allow an in-memory override. */ }
    render(true);
  });
  window.addEventListener('languagechange', () => render(true));
  window.addEventListener('storage', event => {
    if (event.key === preferenceKey || event.key === null) {
      preference = readPreference();
      render(true);
    }
  });
  render(false);
  return select;
}
