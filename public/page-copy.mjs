import {t} from './locale.mjs';

const normalize = value => value.trim().replace(/\s+/g, ' ');

/** Capture only known strings from the page template, before loading any user data. */
export function bindPageCopy(root, catalog) {
  const bindings = [];
  function bind(original, apply) {
    const key = normalize(original);
    const version = /^Download (v[\w.-]+)$/.exec(key);
    const phrase = version ? 'Download {0}' : key;
    if (!Object.hasOwn(catalog, phrase)) return;
    const leading = original.match(/^\s*/)[0];
    const trailing = original.match(/\s*$/)[0];
    bindings.push(() => apply(leading + t(phrase, ...catalog[phrase], ...(version ? [version[1]] : [])) + trailing));
  }

  const nodes = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let node = nodes.nextNode(); node; node = nodes.nextNode()) {
    if (!node.parentElement?.closest('script, style, svg, [data-language-control]'))
      bind(node.data, value => { node.data = value; });
  }
  for (const element of root.querySelectorAll('[aria-label], [alt], [title], meta[name="description"]')) {
    for (const attr of ['aria-label', 'alt', 'title', 'content']) {
      if (element.hasAttribute(attr)) bind(element.getAttribute(attr), value => element.setAttribute(attr, value));
    }
  }
  return () => { for (const apply of bindings) apply(); };
}
