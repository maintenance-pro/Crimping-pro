/* LEONI Crimping Pro — Service Worker
   Rôle unique : rendre l'app RÉELLEMENT installable (Chrome exige un SW actif
   avec un handler 'fetch' pour proposer l'installation native).
   Aucune donnée Firebase n'est mise en cache : uniquement le shell (index.html + icônes). */

const CACHE_NAME = 'leoni-crimping-shell-v5'; // ⚠️ incrémenter à chaque déploiement majeur

const SHELL_ASSETS = [
  './index.html',
  './manifest.json',
  './assets/icon-192.png',
  './assets/icon-512.png',
  './assets/login-bg.jpg',
  './assets/login-bg-mobile.jpg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(SHELL_ASSETS).catch(() => {})) // tolère un asset manquant
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Handler 'fetch' obligatoire (même minimal) pour satisfaire le critère d'installabilité Chrome
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // ⚠️ L'API Cache n'accepte que http/https : les schémas file:, blob:, data:,
  // chrome-extension:… doivent être laissés au navigateur (sinon TypeError sur cache.put)
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return;

  // Jamais de cache pour Firebase / APIs externes → toujours réseau frais (données live)
  const isFirebase = /firebaseio\.com|firebasedatabase\.app|firebasestorage\.googleapis\.com|googleapis\.com|firebaseapp\.com/.test(url.hostname);
  if (event.request.method !== 'GET' || isFirebase) {
    event.respondWith(fetch(event.request));
    return;
  }

  // Shell : réseau d'abord (dernière version), cache en secours si hors-ligne
  event.respondWith(
    fetch(event.request)
      .then((resp) => {
        // On ne met en cache que les réponses complètes et cacheables (pas d'opaque/partielles)
        if (resp && resp.status === 200 && (resp.type === 'basic' || resp.type === 'cors')) {
          const clone = resp.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone)).catch(() => {});
        }
        return resp;
      })
      .catch(() => caches.match(event.request).then((r) => r || caches.match('./index.html')))
  );
});
