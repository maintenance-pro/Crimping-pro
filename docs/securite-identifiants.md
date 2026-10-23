# Fin de l'exposition des identifiants — procédure coordonnée

Branche : `security/no-plaintext-secrets` · Règles : `database.rules.json` · Tests : `security-tests/`

## Constats (22/10/2026)

| Constat | Gravité |
|---|---|
| La base de production répond aux lectures **sans connexion** : les règles déployées ne sont pas celles du dépôt. | Critique |
| `loginIndex` et `users/{uid}` contiennent e-mails, **mots de passe en clair** et PIN (9 entrées, 7 profils). | Critique |
| L'outil « réparation admin » de l'écran de connexion **crée un compte et lui donne le rôle admin** sans vérification. | Critique |
| Mot de passe admin **codé en dur** dans le code (et donc dans l'historique git). | Critique |
| Avec `auth != null` seul, **n'importe qui peut créer un compte** (clé API publique) et lire toute la base. | Critique |
| Tout utilisateur connecté peut modifier son propre rôle. | Élevée |
| Les sauvegardes JSON téléchargées avant une bascule TEST/PROD contiennent les mots de passe. | Élevée |
| La connexion PIN est du code mort : le formulaire est caché et jamais branché. Tout le monde se connecte par e-mail. | Information |
| « Changer MDP » (admin) n'écrivait que la copie en base, **jamais le vrai mot de passe** Firebase. | Information |

## Ce que contient la branche

- Plus aucune écriture de mot de passe ni de PIN dans la base (création d'utilisateur, PIN, « changer MDP » admin, synchronisation `loginIndex`, aide console `forceSyncLogin`).
- Outil de réparation admin retiré de l'écran de connexion et désactivé.
- Plus d'auto-création ni de restauration du rôle admin selon l'adresse e-mail ; mot de passe codé en dur supprimé.
- Création d'utilisateur par une instance Firebase secondaire : la session de l'admin n'est plus remplacée par celle du nouveau compte.
- Un utilisateur ne peut plus envoyer son propre rôle en modifiant son profil.
- Les sauvegardes JSON ne contiennent plus `loginIndex`, ni `password` / `pin`.
- `leoniPurgeSecrets()` : purge en une écriture atomique, avec simulation.

Le changement de mot de passe par l'utilisateur lui-même (Mon profil) était déjà correct et n'est pas modifié.

## Procédure, dans l'ordre

Chaque étape indique son effet, son impact, son contrôle et son retour arrière. **Le retour arrière ne rouvre jamais un accès public et ne remet jamais de mot de passe en base.**

### Étape 0 — Préparer (aucun impact)

1. Console Firebase → Realtime Database → ⋮ → **Exporter le JSON**. Ce fichier contient encore les secrets : rangez-le hors de OneDrive et supprimez-le à l'étape 6.
2. Console → Authentication → Utilisateurs : notez les comptes que vous ne connaissez pas.

### Étape 1 — Publier les nouvelles règles

Console → Realtime Database → **Règles** → coller `database.rules.json` → **Publier**. Contenu exact, étapes détaillées et contrôles : `docs/regles-firebase.md`.

- **Effet** : plus aucune lecture sans connexion. Un compte sans profil validé par un admin, ou désactivé, n'accède à rien. Personne ne peut écrire `password`, `pin` ou `loginIndex`, ni changer son propre rôle ou son activation.
- **Impact** : aucun pour les utilisateurs normaux, le temps réel continue. Dans les **onglets déjà ouverts sur l'ancienne version**, quatre actions admin échouent avec « permission refusée » jusqu'au rechargement : créer un utilisateur, définir un PIN, « changer MDP » admin, synchronisation automatique de l'index. Les écritures hors ligne d'une ancienne version qui contiennent un secret sont refusées à la reconnexion ; les autres passent normalement.
- **Contrôle** : onglet *Simulateur de règles* de la console → lecture de `/interventions` non authentifiée → **refusée**.
- **Retour arrière** : corriger uniquement la règle qui pose problème. Ne jamais republier `".read": true`.

### Étape 2 — Déployer la nouvelle version de l'application

L'application est servie par GitHub Pages : <https://maintenance-pro.github.io/Crimping-pro/index.html>. Le déploiement consiste à fusionner la branche dans la branche publiée par GitHub Pages, après relecture. Dans cette version, `API_BASE_URL` est vide : aucun serveur n'est nécessaire.

- **Impact** : chaque poste doit recharger la page une fois (Ctrl+F5). Terminez d'abord les formulaires en cours : un rechargement perd un bon non enregistré. Le service worker sert le réseau en priorité, donc la nouvelle version arrive au prochain chargement.
- **Retour arrière** : redéployer la version précédente reste possible. Les règles de l'étape 1 l'empêchent de réécrire des secrets.

### Étape 3 — Purger les secrets de la base (administrateur)

Connecté en admin, console du navigateur (F12) :

```js
await leoniPurgeSecrets()                 // simulation : affiche ce qui sera supprimé
await leoniPurgeSecrets({ apply: true })  // suppression en une écriture atomique
await leoniPurgeSecrets()                 // contrôle : doit afficher 0 / 0 / 0
```

- **Retour arrière** : aucun, volontairement : les mots de passe ne doivent plus exister en base.

### Étape 4 — Réinitialiser tous les mots de passe exposés

Tous les mots de passe ont été lisibles publiquement : **ils sont compromis**.

1. **`admin@leoni.com` en premier** : son mot de passe était aussi écrit dans le code.
2. Chaque utilisateur se connecte puis change son mot de passe : **Mon profil → Changer mot de passe**.
3. Comptes inconnus ou inutilisés : les **supprimer** dans la console (Authentication).
4. Adresse réelle : la console peut aussi envoyer un e-mail de réinitialisation.

- **Contrôle** : Équipe → Utilisateurs → colonne « Modifié le … » à jour pour chaque compte.

### Étape 5 — Révoquer les sessions

- Changer un mot de passe met fin aux autres sessions de ce compte, au plus tard à l'expiration du jeton (1 heure).
- **Coupure immédiate** d'un compte : Équipe → Utilisateurs → désactiver. Les règles refusent alors tout accès à la base, sans attendre l'expiration du jeton. Réactivez après le changement de mot de passe.
- Le serveur Spring Boot ajoutera la révocation des jetons (`revokeRefreshTokens`).

### Étape 6 — Supprimer les copies hors base

- Fichiers `leoni_backup_*.json` (sauvegardes avant bascule TEST/PROD) sur les postes admin, et l'export de l'étape 0 : ils contiennent les mots de passe. Supprimez-les, y compris dans la corbeille OneDrive.
- L'historique git contient l'ancien mot de passe admin : il est neutralisé par l'étape 4. Gardez le dépôt privé.

### Étape 7 — Vérifier

- Tests des règles : `cd security-tests && npm run test:rules` (émulateur local, projet `demo-crimping`, jamais la production).
- `leoniPurgeSecrets()` affiche 0 / 0 / 0.
- Chaque rôle se connecte et travaille normalement.

## Limites qui restent après cette procédure

- Tout utilisateur **approuvé** peut encore lire et écrire toutes les données métier (par exemple valider un bon par le SDK sans être labo). C'est l'objet de l'intégration Spring Boot et des règles par branche.
- L'audit peut être modifié par un utilisateur approuvé ; il sera écrit côté serveur.
- La suppression d'un utilisateur dans l'application ne supprime pas son compte Firebase Auth. Les règles lui refusent tout accès (plus de profil), mais le compte doit être supprimé dans la console.

## Le PIN, côté serveur (fait dans cette branche, inactif tant que le serveur n'est pas en service)

Un PIN de quelques chiffres n'a que peu de valeurs possibles : le déplacer côté serveur ne suffit pas. Ce qui est en place (`backend/src/main/java/com/leoni/crimpingpro/pin/`) :

1. **Le PIN n'est utilisable que sur un poste enrôlé.**
   - Un administrateur enrôle le poste depuis Utilisateurs → « Postes PIN ».
   - Le serveur remet un secret aléatoire (256 bits), conservé dans le navigateur de ce poste et chez le serveur sous forme d'empreinte.
   - Le poste est révocable à tout moment. Depuis un autre navigateur, le PIN seul ne sert à rien.
2. **Connexion** = secret du poste + matricule + PIN.
   - Le serveur remet un jeton Firebase personnalisé : mêmes UID, mêmes données.
   - La session est limitée à ce poste, à 12 h, et se ferme avec l'onglet.
3. **Stockage** :
   - empreinte bcrypt de HMAC(poivre secret du serveur, PIN), dans la branche `secure/` ;
   - cette branche est illisible depuis les navigateurs avec les règles de la phase 2, qui doivent donc être publiées **avant** de définir le premier PIN ;
   - PIN de 6 à 8 chiffres ; suites, répétitions et chiffres du matricule refusés.
4. **Limitation des tentatives**, comptée dans la base (valable pour toutes les instances du serveur) et **avant** la vérification, pour que des essais simultanés ne la contournent pas :
   - par compte : 5 erreurs → blocage de 15 min, puis 30, 60… ; 10 erreurs → blocage jusqu'au déblocage par un administrateur ;
   - par poste : 20 erreurs par heure, tous comptes confondus → poste bloqué jusqu'à la fin de l'heure ;
   - par adresse réseau : 30 essais par minute ;
   - chaque connexion, erreur, enrôlement, révocation et changement de PIN est journalisé, sans le PIN.
5. **Révocation d'un poste** :
   - effet immédiat sur l'API et sur les nouvelles lectures ou écritures de la base, grâce aux règles de la phase 2 ;
   - un écran déjà ouvert peut continuer à recevoir des mises à jour au plus jusqu'au renouvellement de son jeton (environ 1 h) ;
   - pour une coupure totale immédiate d'un compte : le désactiver, ou « fin des sessions » côté serveur.
