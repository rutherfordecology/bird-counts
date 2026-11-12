// Bird Count Field: works offline.
// - App files and libraries: cached on install, served cache-first.
// - Map tiles: served from the offline cache when present (downloaded from Setup, or seen before).
const APP = 'bc-app-v0.6.0';
const TILES = 'bc-tiles-v1';
const APP_FILES = [
  './', './index.html', './species.js', './manifest.webmanifest', './icon.svg',
  './sites/index.json', './sites/waimata.geojson',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
  'https://unpkg.com/leaflet@1.9.4/dist/images/layers.png',
  'https://unpkg.com/leaflet@1.9.4/dist/images/layers-2x.png',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(APP).then(c => c.addAll(APP_FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith('bc-app-') && k !== APP).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

const isTile = url => /server\.arcgisonline\.com\/ArcGIS\/rest\/services\/World_Imagery\/MapServer\/tile\//.test(url) || /tile\.opentopomap\.org\//.test(url);
// Topo tiles come from a., b. or c. subdomains: store and look them up under one name.
const tileKey = url => url.replace(/^https:\/\/[abc]\.tile\.opentopomap\.org\//, 'https://a.tile.opentopomap.org/');

self.addEventListener('fetch', e => {
  const url = e.request.url;
  if (e.request.method !== 'GET') return;
  if (isTile(url)) {
    e.respondWith((async () => {
      const cache = await caches.open(TILES), key = tileKey(url);
      const hit = await cache.match(key);
      if (hit) return hit;
      try {
        const r = await fetch(key, { mode: 'cors' });
        if (r.ok) cache.put(key, r.clone());      // keep tiles you've looked at, too
        return r;
      } catch (err) { return new Response('', { status: 504 }); }
    })());
    return;
  }
  // Libraries (versioned URLs): cache first. Our own files: try the network for up to 3 s so
  // updates arrive straight away, otherwise use the saved copy (no signal or weak signal).
  e.respondWith((async () => {
    const cache = await caches.open(APP);
    const hit = await cache.match(e.request, { ignoreSearch: true });
    const own = url.startsWith(self.location.origin);
    if (hit && !own) return hit;
    const net = fetch(e.request).then(r => { if (r.ok) cache.put(e.request, r.clone()); return r; }).catch(() => null);
    const timeout = new Promise(res => setTimeout(() => res(null), hit ? 3000 : 30000));
    return (await Promise.race([net, timeout])) || hit || (await net) || new Response('Offline', { status: 503 });
  })());
});
