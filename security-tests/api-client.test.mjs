// Client du serveur dans index.html (bloc « const api » extrait tel quel), avec fetch et Firebase simulés.
// Aucune dépendance : node --test api-client.test.mjs
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const start = html.indexOf('  const api = {');
const end = html.indexOf('  /* 🆕 Onglets restés ouverts');
assert.ok(start > 0 && end > start, 'bloc api introuvable');
const block = html.slice(start, end);

function load({ responses, mode = 'production' }) {
  const calls = [];
  const fetch = async (url, init) => {
    calls.push({ url, init, body: init.body === undefined ? undefined : JSON.parse(init.body) });
    const r = responses.shift();
    if (r instanceof Error) throw r;
    return { ok: r.status < 400, status: r.status, json: async () => r.body };
  };
  const fbAuth = { currentUser: { getIdToken: async () => 'jeton-test' } };
  const productionSwitch = { _currentMode: mode };
  const window = { crypto: globalThis.crypto };
  const factory = new Function('fetch', 'fbAuth', 'productionSwitch', 'window', 'crypto', 'API_BASE_URL',
    block + '\nreturn api;');
  return { api: factory(fetch, fbAuth, productionSwitch, window, globalThis.crypto, 'https://api.exemple.test/'), calls };
}

test('même identifiant après une coupure réseau, nouveau après succès', async () => {
  const { api, calls } = load({ responses: [new TypeError('Failed to fetch'),
    { status: 200, body: { applied: false, replay: true } },
    { status: 200, body: { applied: true } }] });
  await assert.rejects(api.decide('validate', 42, { reason: 'x' }), /Serveur injoignable/);
  const r = await api.decide('validate', 42, {});
  assert.equal(r.replay, true);
  assert.equal(calls[0].body.requestId, calls[1].body.requestId, 'nouvel essai = même requestId');
  await api.decide('validate', 42, {});
  assert.notEqual(calls[2].body.requestId, calls[1].body.requestId, 'nouvelle décision = nouveau requestId');
  assert.equal(calls[0].url, 'https://api.exemple.test/api/interventions/42/validate');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer jeton-test');
  assert.equal(calls[0].init.credentials, 'omit');
  assert.equal(calls[0].body.mode, 'production');
});

test('délai dépassé : message prudent et même identifiant au nouvel essai', async () => {
  const abort = new Error('aborted'); abort.name = 'AbortError';
  const { api, calls } = load({ responses: [abort, { status: 200, body: { applied: true } }], mode: 'test' });
  await assert.rejects(api.decide('reject', 7, { reason: 'motif' }), /peut-être été enregistrée/);
  await api.decide('reject', 7, { reason: 'motif' });
  assert.equal(calls[0].body.requestId, calls[1].body.requestId);
  assert.equal(calls[1].body.mode, 'test');
  assert.equal(calls[1].body.reason, 'motif');
});

test('refus définitif (409) : message du serveur, identifiant libéré', async () => {
  const { api, calls } = load({ responses: [
    { status: 409, body: { title: 'Action impossible', detail: 'Ce bon a déjà été validé — rechargez la fiche.' } },
    { status: 200, body: { applied: true } }] });
  await assert.rejects(api.decide('validate', 42, {}), /déjà été validé/);
  await api.decide('validate', 42, {});
  assert.notEqual(calls[0].body.requestId, calls[1].body.requestId);
});

test('erreur serveur (503) : nouvel essai possible avec le même identifiant', async () => {
  const { api, calls } = load({ responses: [
    { status: 503, body: { detail: 'Le service est momentanément indisponible.' } },
    { status: 200, body: { applied: true } }] });
  await assert.rejects(api.decide('deliver', 9, {}), /momentanément indisponible/);
  await api.decide('deliver', 9, {});
  assert.equal(calls[0].body.requestId, calls[1].body.requestId);
});

test('le corps ne peut pas imposer un autre requestId ou mode', async () => {
  const { api, calls } = load({ responses: [{ status: 200, body: {} }] });
  await api.decide('validate', 1, { requestId: 'force', mode: 'test' });
  assert.notEqual(calls[0].body.requestId, 'force');
  assert.equal(calls[0].body.mode, 'production');
});

test('connexion par PIN : aucun jeton envoyé, méthode et corps transmis', async () => {
  const { api, calls } = load({ responses: [{ status: 200, body: { customToken: 'x' } }] });
  const res = await api.request('POST', '/api/pin/login', { deviceId: 'd', deviceSecret: 's', matricule: 'M7', pin: '583092' }, { auth: false });
  assert.equal(res.customToken, 'x');
  assert.equal(calls[0].init.headers.Authorization, undefined);
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].body.matricule, 'M7');
});

test('réponse 204 sans corps : succès', async () => {
  const { api } = load({ responses: [{ status: 204, body: undefined }] });
  assert.deepEqual(await api.request('DELETE', '/api/admin/users/u1/pin'), {});
});
