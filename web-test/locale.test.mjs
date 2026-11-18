import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveLanguage, mountLanguage, t, language} from '../public/locale.mjs';
import {ruleLabel, label, liveCopy, scoreRules, audienceNames} from '../public/live-copy.mjs';
import {validateLive} from '../public/live-data.mjs';
import {landingCopy} from '../public/landing-copy.mjs';
import {bindPageCopy} from '../public/page-copy.mjs';

test('choose an explicit supported language, then the first supported browser language, then English', () => {
  assert.equal(resolveLanguage('es', ['de-AT']), 'es');
  assert.equal(resolveLanguage('auto', ['uk-UA','fr-CA','de']), 'fr');
  assert.equal(resolveLanguage('bad', ['DE_at']), 'de');
  assert.equal(resolveLanguage('', ['it','pl']), 'en');
  assert.equal(resolveLanguage('auto', []), 'en');
});

test('picker re-renders the active page, persists only the preference, and returns to browser language', () => {
  class Element extends EventTarget {
    children = [];
    append(...items) { this.children.push(...items); }
    replaceChildren(...items) { this.children = items; }
  }
  const saved = new Map(), changes = [], window = new EventTarget();
  const original = new Map();
  function install(key, value) {
    original.set(key, Object.getOwnPropertyDescriptor(globalThis, key));
    Object.defineProperty(globalThis, key, {value, configurable:true});
  }
  install('window', window);
  install('document', {documentElement:{},createElement:() => new Element()});
  install('navigator', {languages:['de-AT'],language:'de-AT'});
  install('localStorage', {
    getItem:key => saved.get(key) ?? null,
    setItem:(key,value) => saved.set(key,value), removeItem:key => saved.delete(key)
  });
  try {
    const target = new Element();
    const select = mountLanguage(target, value => changes.push(value));
    assert.equal(select.value, 'auto');
    assert.equal(document.documentElement.lang, 'de');
    assert.equal(select.children.length, 5);
    assert.equal(ruleLabel('Singles · to 21, win by two, cap 30'),'Einzel · bis 21, zwei Punkte Vorsprung, maximal 30');
    const live = {schema:1,sport:'padel',mode:'doubles',points:['0','0'],sets:[0,0],
      names:['Me + Partner','Opponent 1 + Opponent 2'],labels:['Me','','Partner',''],server:3,serveName:'Opponent 2',
      setScores:[{a:0,b:0,matchTie:false}],phase:'ready',rules:'',format:'padel,1,6,star,1',scoreCall:'',winner:-1};
    assert.deepEqual(audienceNames(validateLive(live)),{teams:['Me + Partner','Gegner 1 + Gegner 2'],serving:'Gegner 2'});
    assert.equal(scoreRules(live),'Doppel · Star Point · Match-Tie-Break bis 10 im Entscheidungssatz');
    assert.deepEqual(audienceNames({...live,labels:undefined}),{teams:live.names,serving:'Opponent 2'});
    assert.throws(()=>validateLive({...live,labels:['Too few']}));
    assert.throws(()=>validateLive({...live,format:'squash,0,11,advantage,0'}));
    select.value = 'fr';
    select.dispatchEvent(new Event('change'));
    assert.equal(language(), 'fr');
    assert.deepEqual([...saved.values()], ['fr']);
    assert.equal(t('{0} wins','Sieg für {0}','Victoire de {0}','Victoria de {0}','Game {1}'),'Victoire de Game {1}');
    assert.equal(label('{0} won by retirement', 'Open'), 'Victoire de Open sur abandon');
    navigator.languages = ['es-MX'];
    window.dispatchEvent(new Event('languagechange'));
    assert.deepEqual(changes, ['fr']);
    select.value = 'auto';
    select.dispatchEvent(new Event('change'));
    assert.equal(language(), 'es');
    assert.deepEqual([...saved.values()], []);
    navigator.languages = ['it'];
    navigator.language = 'it';
    window.dispatchEvent(new Event('languagechange'));
    assert.deepEqual(changes, ['fr','es','en']);
    assert.equal(document.documentElement.lang,'en');
  } finally {
    for (const [key, descriptor] of original) {
      if (descriptor) Object.defineProperty(globalThis,key,descriptor);
      else delete globalThis[key];
    }
  }
});

test('language templates retain their parameter list in every translation', () => {
  for (const [key, translations] of Object.entries({...landingCopy,...liveCopy})) {
    const params = value => [...value.matchAll(/\{(\d+)\}/g)].map(match => match[1]).sort();
    assert.equal(translations.length, 3, key);
    for (const translation of translations) {
      assert.ok(translation.trim().length, key);
      assert.deepEqual(params(translation), params(key), key);
    }
  }
});

test('static page translation captures the initial template only and supports new version numbers', () => {
  const parentElement = {closest: () => null};
  const title = {data:'  Download v17.4.2 ',parentElement};
  const userName = {data:'My own name',parentElement};
  const nodes = [title,userName];
  let i = 0;
  const setup = Object.getOwnPropertyDescriptor(globalThis,'document');
  const filter = Object.getOwnPropertyDescriptor(globalThis,'NodeFilter');
  const previousNavigator = Object.getOwnPropertyDescriptor(globalThis,'navigator');
  Object.defineProperty(globalThis,'navigator',{configurable:true,value:{languages:['de-AT']}});
  Object.defineProperty(globalThis,'document',{configurable:true,value:{createTreeWalker:() => ({nextNode:()=>nodes[i++]})}});
  Object.defineProperty(globalThis,'NodeFilter',{configurable:true,value:{SHOW_TEXT:4}});
  try {
    const render = bindPageCopy({querySelectorAll:()=>[]}, {'Download {0}':['{0} herunterladen','Télécharger {0}','Descargar {0}']});
    nodes.push({data:'Download v8',parentElement});
    render();
    assert.equal(title.data,'  v17.4.2 herunterladen ');
    assert.equal(userName.data,'My own name');
    assert.equal(nodes[2].data,'Download v8');
  } finally {
    if (setup) Object.defineProperty(globalThis,'document',setup); else delete globalThis.document;
    if (filter) Object.defineProperty(globalThis,'NodeFilter',filter); else delete globalThis.NodeFilter;
    if (previousNavigator) Object.defineProperty(globalThis,'navigator',previousNavigator); else delete globalThis.navigator;
  }
});
