import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

// Shared by the browser tests: a static server for the production build (dist/) with the SPA fallback, and the
// browser already installed on the machine (PW_CHANNEL=msedge, the Windows default, or chrome elsewhere).

const DIST = fileURLToPath(new URL('../dist/', import.meta.url));
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.wasm': 'application/wasm', '.gz': 'application/gzip',
  '.woff2': 'font/woff2', '.txt': 'text/plain' };

/**
 * Serves dist/ on a random local port. `api(request, body)` may answer /api/ requests itself (return
 * {status, json}); without it they get 404, as from a server with that route missing.
 */
export async function serveBuild({ api } = {}) {
  assert.ok(existsSync(join(DIST, 'index.html')), 'run npm run build first');
  const server = createServer(async (request, response) => {
    const path = normalize(decodeURIComponent(new URL(request.url, 'http://x').pathname)).replace(/^([/\\])+/, '');
    if (path.replaceAll('\\', '/').startsWith('api/')) {
      const chunks = [];
      for await (const chunk of request) chunks.push(chunk);
      const answer = api ? await api(request, Buffer.concat(chunks).toString('utf8')) : null;
      response.writeHead(answer?.status ?? 404, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify(answer?.json ?? { status: 404 }));
      return;
    }
    let file = join(DIST, path);
    if (!file.startsWith(DIST) || !existsSync(file) || !extname(file)) file = join(DIST, 'index.html');
    // Vary as Spring sends it, so the service worker's cache matching is tested against the real server's headers.
    response.writeHead(200, { 'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream',
      Vary: 'Origin, Access-Control-Request-Method, Access-Control-Request-Headers' });
    response.end(readFileSync(file));
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return {
    origin: `http://127.0.0.1:${server.address().port}`,
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

export function launchBrowser() {
  const channel = process.env.PW_CHANNEL ?? (process.platform === 'win32' ? 'msedge' : 'chrome');
  return chromium.launch({ channel, headless: true });
}
