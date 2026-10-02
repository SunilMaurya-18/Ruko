const VERSION = '__VERSION__';
const PRECACHE = __PRECACHE__;
const CACHE = `ruko-shell-${VERSION}`;
const OCR_CACHE = 'ruko-ocr-v1';

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

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  if (request.mode === 'navigate') {
    // Navigations are always answered from the cached shell: a share-target URL carries the unmasked
    // message in its query string and must never reach the network.
    event.respondWith(
      caches.open(CACHE)
        .then((cache) => cache.match('/'))
        .then((shell) => shell || fetch(new Request('/', { credentials: 'same-origin' }))),
    );
    return;
  }

  if (url.pathname.startsWith('/ocr/')) {
    event.respondWith(
      caches.open(OCR_CACHE).then((cache) => cache.match(request).then((cached) => cached || fetch(request).then((response) => {
        if (response.ok) cache.put(request, response.clone());
        return response;
      }))),
    );
    return;
  }

  event.respondWith(caches.match(request).then((cached) => cached || fetch(request)));
});
