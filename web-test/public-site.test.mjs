import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {handleImprint, handleSupport} from '../worker/imprint.mjs';
import {landingCopy} from '../public/landing-copy.mjs';

const origin = 'https://squash-tap-andrew.endruuu.chatgpt.site';
const contact = {IMPRINT_EMAIL:'example@example.invalid', IMPRINT_PHONE:'+43123456789'};
const read = (name) => readFileSync(new URL('../' + name, import.meta.url), 'utf8');
const open = (suffix = '', options, env = contact) => handleImprint(new Request(origin + '/imprint' + suffix, options), env);

test('imprint shows the developer and city in each language and keeps contact links out of the initial page', async () => {
  const versions = [
    ['en', 'Imprint &amp; contact', 'Vienna, Austria'],
    ['de', 'Impressum &amp; Kontakt', 'Wien, Österreich'],
    ['fr', 'Mentions légales et contact', 'Vienne, Autriche'],
    ['es', 'Aviso legal y contacto', 'Viena, Austria']
  ];
  for (const [lang, title, city] of versions) {
    const res = await open('?lang=' + lang);
    assert.equal(res.status, 200);
    assert.equal(res.headers.get('content-language'), lang);
    assert.match(res.headers.get('x-robots-tag'), /noindex/);
    assert.match(res.headers.get('cache-control'), /no-store/);
    const page = await res.text();
    assert.ok(page.includes(title));
    assert.ok(page.includes('Andrew Lindley</p><p>' + city + '</p>'));
    assert.match(page, /<form method="post"/);
    assert.ok(!page.includes('mailto:') && !page.includes('tel:'));
    for (const value of Object.values(contact)) assert.ok(!page.includes(value));
  }
  const head = await open('', {method:'HEAD'});
  assert.equal(head.status,200);assert.equal(await head.text(),'');
  const best = await open('', {headers:{'Accept-Language':'es;q=0,fr-CA;q=.7,de-AT;q=.9'}});
  assert.equal(best.headers.get('Content-Language'),'de');
});

test('one deliberate form submission reveals accessible contact links with no JavaScript or public cache', async () => {
  const res = await open('?lang=de', {method:'POST',headers:{Origin:origin,'Sec-Fetch-Site':'same-origin'}});
  assert.equal(res.status,200);
  assert.match(res.headers.get('cache-control'),/no-store/);
  const page = await res.text();
  assert.ok(page.includes('href="mailto:'+contact.IMPRINT_EMAIL+'"'));
  assert.ok(page.includes('href="tel:'+contact.IMPRINT_PHONE+'"'));
  assert.ok(!page.includes('<script'));
  const again = await open('?lang=de');
  assert.ok(!(await again.text()).includes(contact.IMPRINT_EMAIL));
  for (const headers of [{Origin:'https://example.invalid'},{'Sec-Fetch-Site':'cross-site'}]) {
    const cross = await open('',{method:'POST',headers});assert.equal(cross.status,403);
  }
  assert.equal((await open('',{method:'PUT'})).status,405);
  const unavailable=await open('',{method:'POST'},{});
  assert.equal(unavailable.status,503);
  assert.match(await unavailable.text(),/<form method="post"/);
});

test('the public HTML contains the searchable app, accessible screenshots and matching social metadata before scripts run', () => {
  const page = read('public/index.html');
  const title = page.match(/<title>(.*?)<\/title>/)[1];
  const description = page.match(/<meta name="description" content="([^"]+)"/)[1];
  const data = JSON.parse(page.match(/<script type="application\/ld\+json">([^<]+)<\/script>/)[1]);
  assert.equal(data.name, 'Keep Score');assert.equal(data.operatingSystem,'Wear OS');
  assert.equal(data.author.name,'Andrew Lindley');assert.equal(data.url,origin+'/');
  assert.equal(data.description,description);assert.ok(page.includes('property="og:title" content="'+title+'"'));
  assert.ok(page.includes('name="twitter:card" content="summary_large_image"'));
  assert.ok(page.includes('rel="canonical" href="'+origin+'/"'));
  assert.ok(page.includes('property="og:image" content="'+data.image+'"'));
  assert.ok(page.includes('href="/imprint"'));assert.doesNotMatch(page, /mailto:|href="tel:/i);
  assert.deepEqual(landingCopy[title].length,3);assert.deepEqual(landingCopy[description].length,3);
  for (const file of ['watch-home-tiles.png','watch-squash.png','watch-results-history.png']) {
    assert.ok(data.screenshot.includes(origin+'/'+file));assert.ok(page.includes('src="'+file+'"'));
  }
  const card = readFileSync(new URL('../public/keep-score-social.png', import.meta.url));
  assert.equal(card.readUInt32BE(16),1200);assert.equal(card.readUInt32BE(20),630);
});

test('discovery files list public guides and keep match sessions and identifiers out of search', () => {
  const sitemap=read('public/sitemap.xml'), robots=read('public/robots.txt');
  assert.deepEqual([...sitemap.matchAll(/<loc>(.*?)<\/loc>/g)].map(m=>m[1]),[origin+'/',origin+'/watch-menus']);
  assert.ok(robots.includes('Sitemap: '+origin+'/sitemap.xml'));
  assert.ok(!/^Disallow:\s*\/\s*$/m.test(robots));
  for(const name of ['transfer','referee','referee-join','live','diagnostics']) {
    assert.match(read('public/'+name+'.html'),/name="robots" content="noindex,nofollow,nosnippet"/);
  }
});

test('the voluntary project support is a collapsed footer control with four-language copy and a local link', () => {
  const html = read('public/index.html');
  const footer = html.match(/<footer\b[^>]*>([\s\S]+?)<\/footer>/)[1];
  assert.equal(html.indexOf('class="project-support"'), html.lastIndexOf('class="project-support"'));
  const section = footer.match(/<details class="project-support">([\s\S]*?)<\/details>/)[1];
  assert.match(section, /href="\/support" data-support/);
  assert.ok(read('public/landing.mjs').includes('[data-support]'));
  for (const text of section.matchAll(/>([^<>]+)<\//g)) {
    assert.equal(landingCopy[text[1]].length, 3);
  }
});

test('support opens the configured payment page or the local private contact reveal', async () => {
  const request = new Request(origin + '/support?lang=de&url=https://untrusted.invalid');
  const provider = 'https://example.invalid/verified-support';
  const response = handleSupport(request, {SUPPORT_URL: provider});
  assert.equal(response.status, 302);
  assert.equal(response.headers.get('location'), provider);
  assert.equal(response.headers.get('referrer-policy'), 'no-referrer');
  for (const value of ['', 'invalid', 'javascript:void(0)', origin + '/support']) {
    const routed = handleSupport(request, {SUPPORT_URL: value});
    assert.equal(routed.headers.get('location'), origin + '/imprint?lang=de&topic=support#contact');
  }
  const contactPage = await (await open('?lang=de&topic=support')).text();
  assert.match(contactPage, /Du möchtest Keep Score unterstützen/);
  assert.match(contactPage, /topic=support#contact/);
  assert.doesNotMatch(contactPage, /mailto:/);
  const shown = await (await open('?lang=de&topic=support', {method: 'POST', headers: {Origin: origin}})).text();
  assert.ok(shown.includes('mailto:' + contact.IMPRINT_EMAIL + '?subject=' + encodeURIComponent('Keep Score unterstützen')));
  assert.equal(handleSupport(new Request(origin + '/support', {method: 'POST'}), {}).status, 405);
});
