const VERSION = '__VERSION__';
const PRECACHE = __PRECACHE__;
const CACHE = `ruko-shell-${VERSION}`;
const OCR_CACHE = 'ruko-ocr-v1';
const FONT_CACHE = 'ruko-fonts-v1';
// Pages the server renders on the same origin (API docs, health). They must not be answered with the app shell.
const SERVER_PAGES = ['/api/', '/actuator/', '/swagger-ui', '/v3/api-docs'];
// Cached files are same-origin and static. Servers add Vary: Origin, and module scripts and fonts are requested with
// an Origin header the precache request did not have, so honouring Vary would miss the cache exactly when offline.
const MATCH = { ignoreVary: true };

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(PRECACHE))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((key) => key.startsWith('ruko-shell-') && key !== CACHE).map((key) => caches.delete(key)),
      ))
      .then(() => self.clients.claim()),
  );
});

// The pause-journal reminder (fixed text, shown by the page) opens the journal.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => new URL(client.url).origin === self.location.origin);
      return open ? open.navigate('/journal').then((client) => client?.focus()) : self.clients.openWindow('/journal');
    }),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin || SERVER_PAGES.some((prefix) => url.pathname.startsWith(prefix))) return;

  if (request.mode === 'navigate') {
    // Navigations are always answered from the cached shell: a share-target URL carries the unmasked
    // message in its query string and must never reach the network.
    event.respondWith(
      caches.open(CACHE)
        .then((cache) => cache.match('/', MATCH))
        .then((shell) => shell || fetch(new Request('/', { credentials: 'same-origin' }))),
    );
    return;
  }

  const lazyCache = url.pathname.startsWith('/ocr/') ? OCR_CACHE : url.pathname.startsWith('/fonts/') ? FONT_CACHE : null;
  if (lazyCache) {
    event.respondWith(
      caches.open(lazyCache).then((cache) => cache.match(request, MATCH).then((cached) => cached || fetch(request).then((response) => {
        if (response.ok) cache.put(request, response.clone());
        return response;
      }))),
    );
    return;
  }

  event.respondWith(caches.match(request, MATCH).then((cached) => cached || fetch(request)));
});
