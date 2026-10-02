import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
import golden from '../../shared/fixtures/engine-golden.v0.json' with { type: 'json' };

// JournalNetworkTest (plan Phase 5): fill the pause journal in a real browser, then use every feature that talks to
// the server, and assert that no request (URL, headers, or body) ever carries what was written in the journal.
// Runs against the production build (npm run build) with the browser already on the machine: PW_CHANNEL=msedge
// (default on Windows) or chrome (default elsewhere, as on the CI runner).

const DIST = fileURLToPath(new URL('../dist/', import.meta.url));
const WHY = 'JOURNAL-CANARY-my-cousin-says-it-doubles';
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.wasm': 'application/wasm', '.gz': 'application/gzip' };

let server;
let browser;
let origin;

before(async () => {
  assert.ok(existsSync(join(DIST, 'index.html')), 'run npm run build first');
  server = createServer((request, response) => {
    const path = normalize(decodeURIComponent(new URL(request.url, 'http://x').pathname)).replace(/^([/\\])+/, '');
    let file = join(DIST, path);
    if (!file.startsWith(DIST) || !existsSync(file) || !extname(file)) file = join(DIST, 'index.html');
    response.writeHead(200, { 'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream' });
    response.end(readFileSync(file));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  origin = `http://127.0.0.1:${server.address().port}`;
  const channel = process.env.PW_CHANNEL ?? (process.platform === 'win32' ? 'msedge' : 'chrome');
  browser = await chromium.launch({ channel, headless: true });
});

after(async () => {
  await browser?.close();
  await new Promise((resolve) => server?.close(resolve) ?? resolve());
});

test('journal answers never leave the phone', async () => {
  const context = await browser.newContext({ serviceWorkers: 'allow' });
  const requests = [];
  const record = async (request) => {
    requests.push({ url: request.url(), method: request.method(), headers: await request.allHeaders(), body: request.postData() ?? '' });
  };
  context.on('request', (request) => { record(request); });

  // The server is stubbed: analyze answers with a real server response; complaint and voice say "unavailable".
  const analyzed = golden.find((entry) => entry.id === 'sc-001').response;
  await context.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/analyze') return route.fulfill({ json: analyzed });
    return route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"status":503}' });
  });

  const page = await context.newPage();
  page.on('websocket', (socket) => requests.push({ url: socket.url(), method: 'WS', headers: {}, body: '' }));

  await page.goto(`${origin}/journal`);
  const beforeJournal = requests.length;
  await page.locator('textarea').fill(WHY);
  await page.getByLabel('कुछ महीने').check();
  await page.getByLabel('नहीं', { exact: true }).check();
  await page.getByRole('button', { name: '24 घंटे रुकें' }).click();
  await page.waitForTimeout(500);
  const duringJournal = requests.slice(beforeJournal);
  assert.deepEqual(duringJournal.filter((r) => new URL(r.url).pathname.startsWith('/api/')), [],
    'filling the journal makes no API request');

  const stored = await page.evaluate(() => localStorage.getItem('ruko.journal.v1'));
  assert.match(stored, new RegExp(WHY), 'the journal is kept in localStorage');
  await page.reload();
  assert.equal(await page.locator('textarea').inputValue(), WHY, 'and survives a reload');

  // Now everything that does reach the server: check a message, listen, and draft a complaint.
  await page.getByRole('link', { name: 'होम', exact: true }).click();
  await page.locator('textarea').fill('Guaranteed 5% daily returns! Pay to vipprofits@okaxis now.');
  await page.getByRole('button', { name: 'जाँचें', exact: true }).click();
  await page.waitForURL('**/result');
  await page.getByRole('button', { name: /सुनें|Listen/ }).click();
  await page.getByRole('link', { name: /रुकें और सोचें|Pause and think/ }).click();
  await page.waitForURL('**/journal');

  await page.goto(`${origin}/recovery`);
  await page.locator('input[type="date"]').fill('2026-09-01');
  await page.locator('input[inputmode="numeric"]').fill('4999');
  const selects = page.locator('select');
  await selects.nth(0).selectOption('upi');
  await selects.nth(1).selectOption('whatsapp');
  await selects.nth(2).selectOption('upi_id');
  await page.getByRole('button', { name: 'ड्राफ़्ट बनाएँ' }).click();
  await page.locator('textarea').waitFor();
  await page.waitForTimeout(500);

  const api = requests.filter((r) => new URL(r.url).pathname.startsWith('/api/'));
  assert.ok(api.some((r) => r.url.endsWith('/api/v1/analyze') && r.body.includes('Guaranteed')),
    'the check reached the (stubbed) server, and request bodies are being recorded');
  assert.ok(api.some((r) => r.url.endsWith('/api/v1/complaint/draft')), 'the complaint route was tried');

  const fragments = [WHY, 'JOURNAL-CANARY', 'doubles', 'wait_until', 'ruko.journal'];
  for (const r of requests) {
    const seen = `${r.url}\n${JSON.stringify(r.headers)}\n${r.body}`;
    for (const fragment of fragments) {
      assert.ok(!seen.includes(fragment), `${r.method} ${r.url} carries journal data (${fragment})`);
    }
    assert.ok(new URL(r.url).origin === origin, `only same-origin requests: ${r.url}`);
  }
  await context.close();
});
