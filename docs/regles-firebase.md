# Règles de la Realtime Database — publication

Projet `leoni-sertissage-labo`. Deux jeux de règles, testés sur l'émulateur local (projet `demo-crimping`, jamais la production) :

| Fichier | Quand le publier | Tests |
|---|---|---|
| `database.rules.json` (phase 1) | Dès que vous le décidez : il ne dépend ni du serveur ni de Blaze | 21/21 |
| `database.rules.phase2.json` (phase 2) | **Pas maintenant.** Seulement quand l'application passe par le serveur pour les décisions sur les bons (voir `docs/transition-serveur.md`) | 13/13, et les 21 tests de la phase 1 passent aussi sur ces règles |

```bash
cd security-tests
node run-rules-tests.mjs                                                              # phase 1
node run-rules-tests.mjs --rules ../database.rules.phase2.json --test rules-phase2.test.mjs
node run-rules-tests.mjs --rules ../database.rules.phase2.json                        # phase 1 rejouée sur la phase 2
```

## Phase 1 — contenu exact à publier

Le contenu à coller est **le fichier `database.rules.json` en entier**, sans modification :

```json
{
  "rules": {
    ".read": "auth != null && root.child('users').child(auth.uid).child('role').exists() && root.child('users').child(auth.uid).child('active').val() != false",
    ".write": "auth != null && root.child('users').child(auth.uid).child('role').exists() && root.child('users').child(auth.uid).child('active').val() != false",
    "loginIndex": {
      "$key": {
        ".validate": false
      }
    },
    "users": {
      ".indexOn": [
        "role"
      ],
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        "password": {
          ".validate": false
        },
        "pin": {
          ".validate": false
        },
        "role": {
          ".validate": "newData.isString() && newData.val().matches(/^(super_admin|admin|responsable|tl|crimp|crimping|labo|magasinier|operateur|viewer)$/) && auth.uid != $uid && (root.child('users').child(auth.uid).child('role').val() == 'super_admin' || (root.child('users').child(auth.uid).child('role').val() == 'admin' && newData.val() != 'super_admin' && data.val() != 'super_admin'))"
        },
        "active": {
          ".validate": "newData.isBoolean() && auth.uid != $uid && (root.child('users').child(auth.uid).child('role').val() == 'super_admin' || (root.child('users').child(auth.uid).child('role').val() == 'admin' && data.parent().child('role').val() != 'super_admin'))"
        }
      }
    },
    "interventions": {
      ".indexOn": [
        "numBon",
        "status",
        "createdAt",
        "updatedAt"
      ]
    },
    "stockImportHistory": {
      ".indexOn": [
        "timestamp"
      ]
    },
    "stockMovements": {
      ".indexOn": [
        "timestamp",
        "stockId"
      ]
    },
    "auditLog": {
      ".indexOn": [
        "timestamp",
        "uid"
      ]
    },
    "notifications": {
      ".indexOn": [
        "timestamp",
        "read",
        "createdAt"
      ],
      "$uid": {
        ".indexOn": [
          "timestamp",
          "read",
          "createdAt"
        ]
      }
    }
  }
}
```

Ce que ces règles font :

- **Plus aucune lecture ni écriture sans connexion.**
- **Accès réservé aux comptes approuvés** : profil `users/{uid}` avec un rôle (posé par un administrateur) et non désactivé. Un compte créé librement avec la clé publique de l'application n'a pas de profil, donc aucun accès.
- **Exception** : chacun peut lire son propre profil, pour afficher « Compte non configuré » au lieu d'une erreur.
- **Plus aucune écriture de secrets** : `users/*/password`, `users/*/pin` et `loginIndex` sont refusés pour tout le monde. Une écriture qui en contient un est refusée en entier. Les suppressions restent possibles, et la purge en a besoin.
- **Rôles et activation** :
  - personne ne change son propre rôle ni sa propre activation ;
  - seul un administrateur change ceux des autres ;
  - le rôle `super_admin` ne s'attribue ou ne se modifie que par un `super_admin`.
- **Index de requête** (`.indexOn`) : mêmes valeurs que dans le dépôt. Ils n'ouvrent aucun accès.

## Phase 1 — étapes dans la console

1. **Sauvegarde.** Console Firebase → projet *leoni-sertissage-labo* → **Realtime Database** → onglet **Données** → menu ⋮ → **Exporter le JSON**.
   - Rangez le fichier hors de OneDrive : il contient encore les mots de passe.
   - Supprimez-le après la purge (procédure `docs/securite-identifiants.md`, étape 6).
2. **Copie des règles actuelles.** Onglet **Règles** → copiez tout le texte dans un fichier local.
   - Cette copie sert de référence, **pas de retour arrière** : les règles actuelles laissent lire la base sans connexion.
   - Si elles contiennent des `.indexOn` absents du fichier ci-dessus, ajoutez-les au même endroit avant de publier (ils n'ouvrent aucun accès).
3. **Remplacement.** Dans l'onglet **Règles**, sélectionnez tout le texte (Ctrl+A), supprimez-le, puis collez le contenu de `database.rules.json`. La console doit indiquer une syntaxe valide.
4. **Publication.** Cliquez sur **Publier**. L'effet est immédiat (moins d'une minute) sur toutes les connexions.
5. **Contrôle dans le simulateur de règles** (bouton *Rules Playground* ou *Simulateur*, même onglet) :

   | Essai | Résultat attendu |
   |---|---|
   | Lecture `/interventions`, non authentifié | Refusée |
   | Lecture `/interventions`, authentifié avec l'UID d'un compte approuvé | Autorisée |
   | Écriture `/users/<uid>/password`, authentifié avec l'UID d'un administrateur | Refusée |
   | Écriture `/users/<votre uid>/role`, authentifié avec votre UID | Refusée |

6. **Contrôle réel.**
   - Ouvrez l'application avec un compte de chaque rôle : le travail courant doit fonctionner.
   - Dans une fenêtre privée, sans connexion, l'adresse `https://leoni-sertissage-labo-default-rtdb.europe-west1.firebasedatabase.app/interventions.json` doit répondre `Permission denied`.

## Phase 1 — conséquences sur les connexions actuelles

| Qui | Effet |
|---|---|
| Utilisateurs connectés avec un compte approuvé | **Aucun.** Pas de reconnexion ni de rechargement nécessaires ; le temps réel continue. |
| Toute personne ou programme qui lisait la base **sans connexion** | Coupé immédiatement. |
| Compte sans profil, sans rôle, ou désactivé (`active: false`) | Perd tout accès immédiatement, y compris dans un onglet ouvert : l'écran se vide, les écritures sont refusées. |
| Onglets ouverts sur **l'ancienne version** de l'application | Fonctionnement normal, sauf ces actions d'administration, refusées (« permission refusée ») jusqu'au rechargement : création d'un utilisateur, PIN, « Changer MDP » admin, synchronisation de l'index de connexion, outil de réparation. Pour un admin qui modifie **son propre** profil, l'ancienne version renvoie aussi son rôle : l'enregistrement est refusé. Il faut recharger la page. |
| Écritures faites **hors ligne** dans un ancien onglet, envoyées à la reconnexion | Celles qui contiennent un mot de passe ou un PIN sont refusées ; les autres passent normalement. |
| Connexion par e-mail et mot de passe | Inchangée. |
| Connexion par PIN | Déjà inopérante avant ce changement : rien ne change. |

**Retour arrière** : corriger la règle précise qui gêne et republier. **Ne jamais republier `".read": true`** ni une règle qui accepte `password`, `pin` ou `loginIndex`.

## Phase 2 — ce qui change (à publier plus tard)

Fichier `database.rules.phase2.json`. Il reprend la phase 1 à l'identique, avec ces ajouts :

- **Branche `secure/` illisible et non modifiable depuis les navigateurs**, même par un administrateur. Seul le serveur y accède. Elle contient les postes enrôlés et les empreintes de PIN.
  - Pour cela, l'accès n'est plus donné à la racine : il est donné branche par branche.
  - Une entrée `$other` couvre toutes les autres branches, avec la même condition qu'en phase 1, donc le reste de l'application ne change pas.
  - Une lecture de la racine `/` entière est refusée. L'application n'en fait aucune.
- **Statut d'un bon** (`interventions` et `test_data/interventions`) : un navigateur ne peut ni le passer à `validated`, `rejected` ou `delivered`, ni le faire revenir en arrière.
  - Ne peut pas non plus enlever `locked: true`, ni changer `lab/decision`.
  - Ces décisions passent par le serveur.
  - Le brouillon, la soumission, l'édition, la suppression des pièces jointes, le verrou PDR et la préparation PDR restent possibles.
- **`system/`** (mode TEST/PROD, version minimale de l'application) : lecture par tous les comptes approuvés, écriture par un administrateur seulement.
- **Sessions ouvertes par PIN** (jeton portant `pin_device`) : refusées si le poste a été révoqué ou si la connexion date de plus de 12 h. Les sessions par e-mail ne sont pas concernées.

**Conséquence à prévoir** : un onglet resté sur une version sans serveur ne peut plus valider, refuser, livrer ni valider automatiquement. Il reçoit « permission refusée » et rien n'est écrit : le bon reste en attente et réapparaît après rechargement. C'est pourquoi la phase 2 se publie seulement quand tous les postes ont rechargé la nouvelle version (`docs/transition-serveur.md`).
