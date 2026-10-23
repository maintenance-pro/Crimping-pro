# Crimping Pro API (Spring Boot)

Backend qui sécurise les opérations sensibles de Crimping Pro. Firebase reste en place : Authentication, Realtime Database et Storage, avec les mêmes UID et les mêmes données. L'application appelle l'API seulement si `API_BASE_URL` est renseigné dans `index.html`.

## Ce qui est fait

| Élément | État |
|---|---|
| **Vérification du jeton Firebase** de chaque requête (`Authorization: Bearer`) : signature, expiration, projet, révocation, compte désactivé | fait, testé |
| **Identité** : identité et rôle déduits du jeton et du profil protégé, jamais du corps de la requête | fait, testé |
| **Administration des comptes** : création, rôle, activation, vraie réinitialisation du mot de passe, fin des sessions | fait, testé |
| **Décisions sur les bons** : validation et refus labo, livraison du dossier, validation automatique.<br>Une transaction par décision ; un même `requestId` n'est jamais appliqué deux fois.<br>Audit et notification écrits sans doublon. | fait, testé (y compris sur l'émulateur de la base) |
| **PIN sur poste enrôlé** :<br>- empreinte bcrypt + poivre secret ;<br>- essais limités par compte, par poste et par adresse ;<br>- poste révocable ;<br>- session de 12 h au plus. | fait, testé |
| **Fichiers joints contrôlés** : images ou PDF, contenu conforme, 7 Mo au plus | fait, testé |
| **Taille des requêtes limitée** : 64 Ko en général, 16 Mo pour les décisions | fait, testé |
| **Journal d'audit** écrit par le serveur, qui suit le mode TEST/PROD | fait |
| **Erreurs sans détail interne** ; aucun jeton, mot de passe, PIN ni secret de poste dans les journaux | fait, testé |
| **Imports, stock, soumission des bons** | pas encore, voir `docs/matrice-permissions.md` |

Versions : Java 21 (LTS), Spring Boot 4.1.2, Firebase Admin 9.11.0.

## Points d'accès

| Méthode | Adresse | Rôle requis |
|---|---|---|
| GET | `/api/me` | tout compte validé |
| POST | `/api/interventions/{n°}/validate` | admin, super_admin, responsable, labo |
| POST | `/api/interventions/{n°}/reject` | admin, super_admin, responsable, labo |
| POST | `/api/interventions/{n°}/deliver` | admin, super_admin, responsable, labo |
| POST | `/api/interventions/{n°}/auto-validate` | admin, super_admin, responsable, crimp, crimping |
| POST | `/api/pin/login` | public : poste enrôlé + matricule + PIN |
| POST | `/api/admin/users` | admin, super_admin |
| PUT | `/api/admin/users/{uid}/role` · `/active` · `/password` | admin, super_admin |
| POST | `/api/admin/users/{uid}/sessions/revoke` | admin, super_admin |
| PUT · DELETE | `/api/admin/users/{uid}/pin` | admin, super_admin |
| POST | `/api/admin/users/{uid}/pin/unlock` | admin, super_admin |
| POST · GET | `/api/admin/devices` | admin, super_admin |
| POST | `/api/admin/devices/{id}/revoke` | admin, super_admin |
| GET | `/actuator/health` | public, sans détail |

Tout le reste est refusé.

Réponses des décisions :
- `200` avec `applied` (écrite par cette requête) ou `replay` (même requête déjà appliquée) ;
- `409` si l'état du bon ne le permet plus ;
- `422` si le bon est incomplet (coupe obligatoire…) ;
- `404` si le bon n'existe pas.

## Tests

Les outils sont hors du dépôt et hors OneDrive : `$CRIMPING_TOOLS`, par défaut `~/dev-tools/crimping-pro` (JDK 21, Maven 3.9.9, émulateur de la base, dépendances Node des tests de règles).

```bash
cd backend && ./mvn-local.sh test          # 89 tests
cd security-tests && node run-rules-tests.mjs   # règles de la phase 1
```

- La plupart des tests remplacent Firebase par des doublures en mémoire.
- `FirebaseStoreEmulatorTest` démarre l'émulateur local de la Realtime Database (projet `demo-crimping`) pour vérifier les vraies transactions Firebase. Il est ignoré si l'émulateur n'est pas installé.
- Aucun test n'accède au vrai projet.

## Hébergement proposé : Cloud Run

Firebase Hosting n'exécute pas Spring Boot. Le serveur tourne dans un conteneur sur **Cloud Run**, en `europe-west1`, comme la base. Coûts : `docs/estimation-blaze.md`, **à lire avant d'activer Blaze**.

### Prérequis

1. **Formule Blaze** sur le projet Firebase : obligatoire pour Cloud Run.
2. **API à activer** : Cloud Run, Cloud Build, Artifact Registry, Secret Manager, IAM Service Account Credentials.
3. **Compte de service dédié** `crimping-pro-api@leoni-sertissage-labo.iam.gserviceaccount.com`, avec ces seuls rôles :
   - `roles/firebaseauth.admin` : comptes, mots de passe, sessions ;
   - `roles/firebasedatabase.admin` : bons, profils, audit, branche `secure/` ;
   - `roles/iam.serviceAccountTokenCreator` **sur ce compte de service lui-même** : signature des jetons de session du PIN, sans fichier de clé ;
   - `roles/secretmanager.secretAccessor` **sur le seul secret** `crimping-pin-pepper`.

   Aucun fichier de clé : Cloud Run fournit l'identité.
4. **Secret du PIN**, à créer seulement à l'étape G de `docs/transition-serveur.md`, après les règles de la phase 2 :

   ```bash
   openssl rand -base64 48 | gcloud secrets create crimping-pin-pepper --data-file=-
   ```

   Ce secret n'est jamais écrit dans le dépôt, un fichier ou une conversation. S'il change, tous les PIN sont à redéfinir. Sans lui, la connexion par PIN est désactivée et le reste de l'API fonctionne.

### Déploiement (une fois Blaze validé ; rien n'est lancé d'ici)

```bash
gcloud run deploy crimping-pro-api \
  --source backend \
  --region europe-west1 \
  --service-account crimping-pro-api@leoni-sertissage-labo.iam.gserviceaccount.com \
  --set-env-vars CORS_ALLOWED_ORIGINS=https://maintenance-pro.github.io \
  --min-instances 0 --max-instances 2 \
  --allow-unauthenticated
# Étape G seulement :  --set-secrets PIN_PEPPER=crimping-pin-pepper:latest
```

- `--allow-unauthenticated` ouvre l'accès réseau, pas les données. Chaque appel `/api/**` doit porter un jeton Firebase valide, sinon il reçoit 401. Seule exception : `/api/pin/login`, qui exige un poste enrôlé.
- Origine CORS :
  - L'application est servie par `https://maintenance-pro.github.io/Crimping-pro/`. Une origine ne contient pas de chemin : `https://maintenance-pro.github.io` autorise toutes les pages GitHub Pages de ce compte.
  - La protection réelle reste le jeton Firebase. Un domaine dédié à l'application réduirait cette origine.

Ensuite :
1. Mettre l'adresse donnée par Cloud Run dans `API_BASE_URL` de `index.html`.
2. Suivre l'ordre de `docs/transition-serveur.md`.

### Variante : même domaine que l'application

Si l'application passe un jour sur Firebase Hosting, une réécriture `/api/**` vers le service Cloud Run évite le CORS :

```json
{ "hosting": { "rewrites": [ { "source": "/api/**", "run": { "serviceId": "crimping-pro-api", "region": "europe-west1" } } ] } }
```

## Retour arrière

- Le serveur ne fait qu'ajouter des points d'accès. Tant que `API_BASE_URL` est vide, l'arrêter ne change rien.
- Après branchement : remettre `API_BASE_URL = ''` et redéployer l'application. Si les règles de la phase 2 sont publiées, republier d'abord la phase 1 (`docs/transition-serveur.md`).
- **Le retour arrière ne rouvre jamais un accès public et ne remet jamais de mot de passe en base.**
