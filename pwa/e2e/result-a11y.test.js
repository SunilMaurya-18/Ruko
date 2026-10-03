import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import golden from '../../shared/fixtures/engine-golden.v0.json' with { type: 'json' };
import fixtures from '../../shared/fixtures/fixtures.v0.json' with { type: 'json' };
import { launchBrowser, serveBuild } from './support.js';

// The Phase 6 accessibility pass, on the result screen at a small-phone size: what TalkBack relies on (focus on
// arrival, a live band, names on every control, language tags, heading order) and what a shaky thumb needs (48 px
// targets, no sideways scrolling). The UI is light-only, so it must stay light even with the phone in dark mode.

const ROLES_NEEDING_NAMES = new Set(['button', 'link', 'textbox', 'combobox', 'checkbox', 'radio', 'listbox']);

let server;
let browser;

before(async () => {
  server = await serveBuild();
  browser = await launchBrowser();
});

after(async () => {
  await browser?.close();
  await server?.close();
});

const CASES = [['sc-001', 'light'], ['sc-002', 'light'], ['ed-002', 'light'], ['sc-001', 'dark'], ['ed-002', 'dark']];

for (const [id, colorScheme] of CASES) {
  test(`result screen is usable with a screen reader and a thumb (${id}, ${colorScheme})`, async () => {
    const response = golden.find((entry) => entry.id === id).response;
    const context = await browser.newContext({ viewport: { width: 360, height: 740 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, colorScheme });
    await context.route('**/api/**', (route) => (new URL(route.request().url()).pathname === '/api/v1/analyze'
      ? route.fulfill({ json: response })
      : route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503}' })));
    const page = await context.newPage();

    await page.goto(`${server.origin}/`);
    await page.locator('textarea').fill(fixtures.find((fixture) => fixture.id === id).text);
    await page.getByRole('button', { name: 'जाँचें', exact: true }).click();
    await page.waitForURL('**/result');
    await page.locator('.band-label').waitFor();

    assert.equal(await page.evaluate(() => document.activeElement?.id), 'result-title', 'focus moves to the screen title');
    assert.equal(await page.locator('section[aria-labelledby="result-title"]').getAttribute('lang'), response.language,
      'the result is tagged with its language so TalkBack picks the right voice');
    assert.ok(await page.evaluate(() => document.documentElement.lang), 'the page has a language');
    const band = page.getByRole('status').filter({ has: page.locator('.band-label') });
    assert.match(await band.innerText(), /\S/, 'the band is announced from a live region');

    const cdp = await context.newCDPSession(page);
    const { nodes } = await cdp.send('Accessibility.getFullAXTree');
    const controls = nodes.filter((node) => !node.ignored && ROLES_NEEDING_NAMES.has(node.role?.value));
    assert.ok(controls.length >= 4, `the accessibility tree lists the controls (${controls.length})`);
    assert.deepEqual(controls.filter((node) => !node.name?.value?.trim()).map((node) => node.role.value), [],
      'every control has an accessible name');

    assert.ok(await page.locator('.touch:visible').count() >= 4, 'the screen has touch targets to measure');
    const small = await page.locator('.touch:visible').evaluateAll((elements) => elements
      .map((element) => ({ text: element.textContent.trim(), box: element.getBoundingClientRect() }))
      .filter(({ box }) => box.width < 48 || box.height < 48)
      .map(({ text, box }) => `${text} ${Math.round(box.width)}x${Math.round(box.height)}`));
    assert.deepEqual(small, [], 'touch targets are at least 48 px');

    const overflow = await page.evaluate(() => document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth);
    assert.ok(overflow <= 0, `no sideways scrolling at 360 px (overflow ${overflow}px)`);

    const levels = await page.locator('main h1, main h2, main h3, main h4').evaluateAll((headings) => headings.map((h) => Number(h.tagName[1])));
    assert.equal(levels[0], 1, 'the screen starts with its title');
    levels.forEach((level, i) => assert.ok(i === 0 || level <= levels[i - 1] + 1, `heading levels do not skip (${levels})`));

    assert.equal(await page.locator('img:not([alt])').count(), 0, 'images have alt text');

    const [pageBg, cardBg] = await page.evaluate(() => [
      getComputedStyle(document.documentElement).backgroundColor,
      getComputedStyle(document.querySelector('.band')).backgroundColor,
    ]);
    const dark = (rgb) => rgb.match(/\d+/g).slice(0, 3).map(Number).reduce((sum, c) => sum + c, 0) < 3 * 128;
    assert.equal(dark(pageBg), false, `the page stays light with the phone in ${colorScheme} mode (${pageBg})`);
    assert.equal(dark(cardBg), false, `the band card stays light with the phone in ${colorScheme} mode (${cardBg})`);
    await context.close();
  });
}
