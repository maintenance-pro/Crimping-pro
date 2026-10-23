// Tests des règles Realtime Database sur l'émulateur local (projet « demo-crimping » : jamais la production).
// Lancement : node run-rules-tests.mjs (démarre l'émulateur et fournit RULES_FILE)
import { test, before, beforeEach, after } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertFails, assertSucceeds
} from '@firebase/rules-unit-testing';
import { ref, get, set, update, push, remove } from 'firebase/database';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-crimping',
    database: {
      host: '127.0.0.1',
      port: 9000,
      rules: readFileSync(process.env.RULES_FILE || new URL('../database.rules.json', import.meta.url), 'utf8')
    }
  });
});

after(async () => { await env?.cleanup(); });

// Jeu de données de départ, écrit sans règles
beforeEach(async () => {
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(async ctx => {
    await set(ref(ctx.database(), '/'), {
      users: {
        sa:       { uid: 'sa', role: 'super_admin', active: true, displayName: 'Super' },
        admin1:   { uid: 'admin1', role: 'admin', active: true, displayName: 'Admin' },
        crimp1:   { uid: 'crimp1', role: 'crimp', active: true, displayName: 'Tech' },
        labo1:    { uid: 'labo1', role: 'labo', active: true, displayName: 'Labo' },
        inactif:  { uid: 'inactif', role: 'crimp', active: false, displayName: 'Ancien' },
        legacy:   { uid: 'legacy', role: 'crimp', active: true, password: 'ancien', pin: '1234' }
      },
      loginIndex: { L1: { email: 'x@y', password: 'ancien', pin: '1234' } },
      interventions: { 101: { numBon: 101, status: 'draft' } }
    });
  });
});

const db = uid => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).database();

// ── Accès général ────────────────────────────────────────────────────────────
test('sans connexion : lecture refusée', async () => {
  await assertFails(get(ref(db(null), 'interventions')));
  await assertFails(get(ref(db(null), 'loginIndex')));
});

test('compte connecté sans profil (inscription libre) : lecture et écriture refusées', async () => {
  await assertFails(get(ref(db('inconnu'), 'interventions')));
  await assertFails(set(ref(db('inconnu'), 'interventions/999'), { numBon: 999 }));
});

test('compte sans profil : peut lire SON profil (vide) pour afficher « Compte non configuré »', async () => {
  await assertSucceeds(get(ref(db('inconnu'), 'users/inconnu')));
  await assertFails(get(ref(db('inconnu'), 'users/admin1')));
});

test('compte désactivé : lecture refusée', async () => {
  await assertFails(get(ref(db('inactif'), 'interventions')));
});

test('utilisateur approuvé : lit et écrit les données métier (fonctionnement actuel conservé)', async () => {
  await assertSucceeds(get(ref(db('crimp1'), 'interventions')));
  await assertSucceeds(update(ref(db('crimp1'), 'interventions/101'), { status: 'submitted' }));
  await assertSucceeds(set(ref(db('crimp1'), 'users/crimp1/lastLoginAt'), Date.now()));
  await assertSucceeds(push(ref(db('crimp1'), 'notifications/labo1'), { message: 'Nouveau bon', read: false }));
});

// ── Secrets : plus aucune écriture possible, même par une ancienne version ──
test('écrire un mot de passe dans un profil : refusé (même pour un admin)', async () => {
  await assertFails(set(ref(db('crimp1'), 'users/crimp1/password'), 'x'));
  await assertFails(set(ref(db('admin1'), 'users/crimp1/password'), 'x'));
});

test('écrire un PIN dans un profil : refusé', async () => {
  await assertFails(set(ref(db('admin1'), 'users/crimp1/pin'), '0000'));
});

test('recréer une entrée loginIndex : refusé', async () => {
  await assertFails(set(ref(db('admin1'), 'loginIndex/K'), { email: 'a@b', password: 'p' }));
});

test('ancienne création d’utilisateur (profil avec password + pin) : refusée en entier', async () => {
  await assertFails(set(ref(db('admin1'), 'users/nouveau'), {
    uid: 'nouveau', role: 'crimp', active: true, password: 'secret1', pin: '1111'
  }));
});

test('purge : un admin supprime loginIndex et les champs password/pin', async () => {
  await assertSucceeds(update(ref(db('admin1')), {
    'loginIndex': null, 'users/legacy/password': null, 'users/legacy/pin': null
  }));
});

// ── Rôles et activation ──────────────────────────────────────────────────────
test('changer son propre rôle : refusé', async () => {
  await assertFails(set(ref(db('crimp1'), 'users/crimp1/role'), 'admin'));
  await assertFails(set(ref(db('admin1'), 'users/admin1/role'), 'super_admin'));
});

test('ancien outil de réparation (nouveau compte qui se donne le rôle admin) : refusé', async () => {
  await assertFails(set(ref(db('pirate'), 'users/pirate'), { uid: 'pirate', role: 'admin', active: true }));
});

test('un admin change le rôle d’un autre utilisateur : accepté', async () => {
  await assertSucceeds(set(ref(db('admin1'), 'users/crimp1/role'), 'labo'));
});

test('un non-admin change le rôle d’un autre : refusé', async () => {
  await assertFails(set(ref(db('labo1'), 'users/crimp1/role'), 'labo'));
});

test('rôle inconnu : refusé', async () => {
  await assertFails(set(ref(db('admin1'), 'users/crimp1/role'), 'chef_supreme'));
});

test('super_admin : seul un super_admin l’accorde ou le modifie', async () => {
  await assertFails(set(ref(db('admin1'), 'users/crimp1/role'), 'super_admin'));
  await assertSucceeds(set(ref(db('sa'), 'users/crimp1/role'), 'super_admin'));
  await assertFails(set(ref(db('admin1'), 'users/sa/role'), 'crimp'));
});

test('création d’un utilisateur par un admin (nouvelle version, sans secret) : acceptée', async () => {
  await assertSucceeds(set(ref(db('admin1'), 'users/nouveau'), {
    uid: 'nouveau', displayName: 'Nouveau', matricule: '123', email: 'n@leoni.com',
    role: 'crimp', active: true, createdAt: Date.now()
  }));
});

test('un admin modifie son propre nom (sans rôle) : accepté', async () => {
  await assertSucceeds(update(ref(db('admin1'), 'users/admin1'), { displayName: 'Admin 2', matricule: '99' }));
});

test('un admin modifie son nom en renvoyant son rôle (ancienne version) : refusé', async () => {
  await assertFails(update(ref(db('admin1'), 'users/admin1'), { displayName: 'Admin 2', role: 'admin' }));
});

test('activation : un admin désactive un autre compte, pas le sien ni un super_admin', async () => {
  await assertSucceeds(set(ref(db('admin1'), 'users/crimp1/active'), false));
  await assertFails(set(ref(db('admin1'), 'users/admin1/active'), false));
  await assertFails(set(ref(db('admin1'), 'users/sa/active'), false));
  await assertFails(set(ref(db('crimp1'), 'users/crimp1/active'), true));
});

test('un admin supprime un profil : accepté', async () => {
  await assertSucceeds(remove(ref(db('admin1'), 'users/legacy')));
});
