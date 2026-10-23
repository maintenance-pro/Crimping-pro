// Règles de la phase 2 (database.rules.phase2.json) sur l'émulateur local (projet « demo-crimping »).
// À publier seulement quand l'application passe par le serveur pour les décisions sur les bons.
// Lancement : node run-rules-tests.mjs --rules ../database.rules.phase2.json --test rules-phase2.test.mjs
// Les tests de la phase 1 doivent aussi passer sur ces règles :
//   node run-rules-tests.mjs --rules ../database.rules.phase2.json
import { test, before, beforeEach, after } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertFails, assertSucceeds
} from '@firebase/rules-unit-testing';
import { ref, get, set, update, push, remove, runTransaction } from 'firebase/database';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-crimping',
    database: {
      host: '127.0.0.1',
      port: 9000,
      rules: readFileSync(process.env.RULES_FILE || new URL('../database.rules.phase2.json', import.meta.url), 'utf8')
    }
  });
});

after(async () => { await env?.cleanup(); });

const bon = (status, extra = {}) => ({ numBon: 1, status, locked: status === 'validated' || status === 'delivered', ...extra });

beforeEach(async () => {
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(async ctx => {
    await set(ref(ctx.database(), '/'), {
      users: {
        admin1: { uid: 'admin1', role: 'admin', active: true },
        crimp1: { uid: 'crimp1', role: 'crimp', active: true, matricule: 'M1' },
        labo1: { uid: 'labo1', role: 'labo', active: true },
        op1: { uid: 'op1', role: 'crimp', active: true, matricule: 'M7' }
      },
      secure: {
        devices: { poste1: { active: true, secretHash: 'abc' }, revoque: { active: false, secretHash: 'def' } },
        pins: { op1: { hash: '$2a$x' } }
      },
      system: { mode: 'production' },
      interventions: {
        1: bon('submitted'),
        2: bon('validated', { lab: { decision: 'validated', coupeMetallo: { fileName: 'c.png' } } }),
        3: bon('draft')
      },
      test_data: { interventions: { 1: bon('submitted') } },
      tools: { t1: { serial: 'S1' } },
      counters: { registre: 10 }
    });
  });
});

const db = (uid, claims) => (uid ? env.authenticatedContext(uid, claims) : env.unauthenticatedContext()).database();
const nowS = () => Math.floor(Date.now() / 1000);

// ── Branche secure : réservée au serveur ─────────────────────────────────────
test('secure : illisible et non modifiable, même par un administrateur', async () => {
  await assertFails(get(ref(db('admin1'), 'secure')));
  await assertFails(get(ref(db('admin1'), 'secure/pins/op1')));
  await assertFails(set(ref(db('admin1'), 'secure/devices/pirate'), { active: true }));
  await assertFails(set(ref(db('crimp1'), 'secure/pins/crimp1'), { hash: 'x' }));
});

// ── Le reste de l'application fonctionne comme avant ─────────────────────────
test('branches non nommées (outils, compteur des bons, test_data…) : accès inchangé', async () => {
  await assertSucceeds(get(ref(db('crimp1'), 'tools')));
  await assertSucceeds(update(ref(db('crimp1'), 'tools/t1'), { cyclesCum: 5 }));
  await assertSucceeds(runTransaction(ref(db('crimp1'), 'counters/registre'), v => (v || 0) + 1));
  await assertSucceeds(get(ref(db('crimp1'), 'test_data/interventions')));
  await assertSucceeds(push(ref(db('crimp1'), 'pdrChangeHistory'), { numBon: 1 }));
  await assertSucceeds(push(ref(db('crimp1'), 'notifications/labo1'), { read: false }));
  await assertFails(get(ref(db(null), 'tools')));
  await assertFails(get(ref(db('inconnu'), 'tools')));
});

test('création, brouillon, soumission et édition d’un bon : accepté', async () => {
  await assertSucceeds(set(ref(db('crimp1'), 'interventions/4'), bon('draft', { locked: false })));
  await assertSucceeds(update(ref(db('crimp1'), 'interventions/3'), { status: 'submitted', 'sla/submittedAt': 1 }));
  await assertSucceeds(update(ref(db('crimp1'), 'interventions/1'), { 'crimping/observation': 'ok' }));
});

test('après décision : pièces jointes supprimables, verrou PDR et préparation PDR modifiables', async () => {
  await assertSucceeds(remove(ref(db('labo1'), 'interventions/2/lab/coupeMetallo')));
  await assertSucceeds(runTransaction(ref(db('labo1'), 'interventions/2/pdrAutoRecord'), c => c ? undefined : { at: 1 }));
  await assertSucceeds(update(ref(db('crimp1'), 'interventions/2'), { 'pdrPreparation/status': 'ready', updatedAt: 2 }));
});

// ── Décisions : uniquement par le serveur ────────────────────────────────────
test('ancienne validation écrite par le navigateur : refusée en entier', async () => {
  await assertFails(update(ref(db('labo1'), 'interventions/1'), {
    status: 'validated', lab: { decision: 'validated' }, '_indexes/status': 'validated', locked: true
  }));
  let saved;
  await env.withSecurityRulesDisabled(async ctx => {
    saved = (await get(ref(ctx.database(), 'interventions/1'))).val();
  });
  if (saved.status !== 'submitted' || saved.lab) throw new Error('bon modifié');
});

test('refus, livraison ou validation automatique depuis le navigateur : refusés', async () => {
  await assertFails(update(ref(db('labo1'), 'interventions/1'), { status: 'rejected' }));
  await assertFails(update(ref(db('labo1'), 'interventions/2'), { status: 'delivered' }));
  await assertFails(update(ref(db('crimp1'), 'interventions/3'), { status: 'validated', autoValidated: true }));
});

test('mêmes protections en mode TEST (test_data)', async () => {
  await assertFails(update(ref(db('labo1'), 'test_data/interventions/1'), { status: 'validated' }));
  await assertSucceeds(update(ref(db('crimp1'), 'test_data/interventions/1'), { status: 'draft' }));
});

test('un bon décidé ne peut pas être rouvert ni déverrouillé', async () => {
  await assertFails(update(ref(db('admin1'), 'interventions/2'), { status: 'submitted' }));
  await assertFails(update(ref(db('admin1'), 'interventions/2'), { locked: false }));
  await assertFails(set(ref(db('admin1'), 'interventions/2/lab/decision'), 'rejected'));
  await assertFails(set(ref(db('admin1'), 'interventions/2'), bon('draft', { locked: false })));
  await assertFails(set(ref(db('labo1'), 'interventions/1/lab/decision'), 'validated'));
});

// ── system : écriture réservée aux administrateurs ───────────────────────────
test('system : lecture par tous les comptes approuvés, écriture par un administrateur', async () => {
  await assertSucceeds(get(ref(db('crimp1'), 'system/mode')));
  await assertSucceeds(get(ref(db('crimp1'), 'system/clientPolicy/minBuild')));
  await assertFails(set(ref(db('crimp1'), 'system/mode'), 'test'));
  await assertFails(set(ref(db('crimp1'), 'system/clientPolicy'), { minBuild: 1 }));
  await assertSucceeds(set(ref(db('admin1'), 'system/mode'), 'test'));
});

// ── Sessions ouvertes par PIN sur un poste enrôlé ────────────────────────────
test('session PIN sur un poste actif et récente : accès normal', async () => {
  const pin = db('op1', { pin_device: 'poste1', auth_time: nowS() - 60 });
  await assertSucceeds(get(ref(pin, 'interventions')));
  await assertSucceeds(update(ref(pin, 'interventions/3'), { 'crimping/observation': 'x' }));
});

test('session PIN sur un poste révoqué ou inconnu : tout est refusé', async () => {
  await assertFails(get(ref(db('op1', { pin_device: 'revoque', auth_time: nowS() }), 'interventions')));
  await assertFails(get(ref(db('op1', { pin_device: 'inconnu', auth_time: nowS() }), 'tools')));
  await assertFails(set(ref(db('op1', { pin_device: 'revoque', auth_time: nowS() }), 'tools/t1/x'), 1));
});

test('session PIN de plus de 12 heures : refusée (nouvelle saisie du PIN)', async () => {
  const old = db('op1', { pin_device: 'poste1', auth_time: nowS() - 13 * 3600 });
  await assertFails(get(ref(old, 'interventions')));
  await assertFails(get(ref(old, 'system/mode')));
});

test('session par e-mail et mot de passe : non concernée par la limite de 12 heures', async () => {
  await assertSucceeds(get(ref(db('crimp1', { auth_time: nowS() - 20 * 3600 }), 'interventions')));
});
