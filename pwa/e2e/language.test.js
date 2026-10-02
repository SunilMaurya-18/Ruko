import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import golden from '../../shared/fixtures/engine-golden.v0.json' with { type: 'json' };
import fixtures from '../../shared/fixtures/fixtures.v0.json' with { type: 'json' };
import { launchBrowser, serveBuild } from './support.js';

// The header switch changes the whole app and the answer language, survives a reload, and Hindi text uses the
// self-hosted Tiro Devanagari Hindi font (never a font CDN).

let server;
let browser;
const requests = [];

before(async () => {
  const byLang = Object.fromEntries(['hi', 'en'].map((lang) => [lang, golden.find((entry) => entry.response.language === lang)]));
  server = await serveBuild({
    api: (request, body) => {
      if (request.url !== '/api/v1/analyze') return null;
      requests.push(JSON.parse(body));
      return { status: 200, json: byLang[JSON.parse(body).lang].response };
    },
  });
  browser = await launchBrowser();
});

after(async () => {
  await browser?.close();
  await server?.close();
});

test('Hindi is the default and uses Tiro Devanagari Hindi from this site', async () => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const fonts = [];
  page.on('request', (request) => {
    if (request.resourceType() === 'font') fonts.push(request.url());
  });

  await page.goto(`${server.origin}/`);
  await page.getByRole('heading', { level: 1, name: 'रुको' }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.lang), 'hi');
  assert.equal(await page.getByRole('button', { name: 'हिंदी' }).getAttribute('aria-pressed'), 'true');
  assert.match(await page.locator('h1').evaluate((h1) => getComputedStyle(h1).fontFamily), /^"Tiro Devanagari Hindi"/);
  assert.ok(await page.evaluate(() => document.fonts.ready.then(() => document.fonts.check('16px "Tiro Devanagari Hindi"', 'रुको'))),
    'the Hindi font loaded');
  assert.ok(fonts.length > 0 && fonts.every((url) => url.startsWith(`${server.origin}/fonts/`)), `fonts are self-hosted: ${fonts}`);
  await context.close();
});

test('switching to English changes the app and the answer, and survives a reload', async () => {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${server.origin}/`);

  await page.getByRole('button', { name: 'English' }).click();
  await page.getByRole('heading', { level: 1, name: 'Ruko' }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.lang), 'en');
  assert.equal(await page.title(), 'Ruko');
  assert.equal(await page.getByRole('button', { name: 'English' }).getAttribute('aria-pressed'), 'true');
  assert.ok(await page.getByRole('link', { name: 'Method', exact: true }).isVisible(), 'the menu is in English');
  assert.doesNotMatch(await page.locator('h1').evaluate((h1) => getComputedStyle(h1).fontFamily), /Tiro/);

  await page.reload();
  await page.getByRole('heading', { level: 1, name: 'Ruko' }).waitFor();

  await page.locator('textarea').fill(fixtures.find((fixture) => fixture.id === 'sc-001').text);
  await page.getByRole('button', { name: 'Check', exact: true }).click();
  await page.waitForURL('**/result');
  await page.getByRole('heading', { level: 1, name: 'Result' }).waitFor();
  assert.equal(requests.at(-1).lang, 'en', 'the check asks for an English answer');

  await page.getByRole('button', { name: 'हिंदी' }).click();
  await page.getByRole('link', { name: 'होम', exact: true }).waitFor();
  assert.match(await page.locator('.notice[lang="hi"]').innerText(), /अंग्रेज़ी/, 'the user is told the result is still in English');
  assert.equal(await page.locator('section[aria-labelledby="result-title"]').getAttribute('lang'), 'en');
  const stored = await page.evaluate(() => ({ ...localStorage }));
  assert.equal(stored['ruko.lang'], 'hi');
  const message = fixtures.find((fixture) => fixture.id === 'sc-001').text;
  assert.ok(Object.values(stored).every((value) => !value.includes(message.slice(0, 20))), 'the message is not stored');
  await context.close();
});
