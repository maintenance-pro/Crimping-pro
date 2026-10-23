# Matrice des permissions

Établie à partir de l'application réelle : menu (`data-requires-role`), `auth.can()`, contrôles de chaque écran, et règles `database.rules.json`.

**Abréviations** : SA = super_admin · A = admin · R = responsable · TL = tl · C = crimp / crimping · L = labo · M = magasinier · O = operateur

**Contrôle aujourd'hui** :
- *navigateur* : seule l'interface vérifie, un compte approuvé peut contourner par le SDK ;
- *règles* : la base refuse elle-même ;
- *serveur* : vérifié par l'API Spring Boot.

## Déjà sécurisé

| Opération | Autorisés | Contrôle aujourd'hui |
|---|---|---|
| Lire ou écrire des données | Compte avec profil validé (rôle posé par un admin) et actif | règles |
| Écrire un mot de passe, un PIN ou `loginIndex` | Personne | règles (`.validate: false`) |
| Changer son propre rôle ou son activation | Personne | règles + serveur |
| Attribuer un rôle, activer ou désactiver un compte | A, SA (super_admin réservé à SA) | règles + serveur |
| Créer un compte | A, SA | serveur (`POST /api/admin/users`) ; navigateur via instance secondaire en attendant |
| Réinitialiser le mot de passe d'un autre | A, SA (pas le sien, pas un SA sauf par un SA) | serveur (`PUT /api/admin/users/{uid}/password`) |
| Mettre fin aux sessions d'un compte | A, SA | serveur (`POST /api/admin/users/{uid}/sessions/revoke`) |
| Décision labo : valider, refuser un bon | A, SA, R, L | serveur, une transaction par décision (`/api/interventions/{n°}/validate`, `/reject`) ; règles de la phase 2 pour interdire l'écriture directe |
| Livraison du dossier (homologation, requalification) | A, SA, R, L | serveur (`/deliver`, seulement si « validé » et bon du bon type) ; règles de la phase 2 |
| Validation automatique (réglage mécanique seul) et déblocage de l'outil | A, SA, R, C | serveur (`/auto-validate`, condition revérifiée) ; règles de la phase 2 |
| Connexion par PIN | compte avec PIN, sur poste enrôlé | serveur (`/api/pin/login`) ; branche `secure/` fermée par les règles de la phase 2 |
| Enrôler ou révoquer un poste, définir ou débloquer un PIN | A, SA | serveur (`/api/admin/devices`, `/api/admin/users/{uid}/pin`) |
| Mode TEST / PROD et version minimale (`system/`) | A, SA | règles de la phase 2 |

Les lignes « serveur » ne protègent réellement qu'une fois `API_BASE_URL` renseigné **et** les règles de la phase 2 publiées : avant cela, l'écriture directe dans la base reste possible. Ordre : `docs/transition-serveur.md`.

## À migrer (ordre proposé)

| Phase | Opération | Autorisés (application actuelle) | Contrôle aujourd'hui | Cible |
|---|---|---|---|---|
| 2 | Compteur de cycles d'un outil (ne doit que monter) | via bons et import Komax | transaction navigateur | règle `newData >= data` + API |
| 3 | Créer, modifier, soumettre un bon | A, SA, R, C (`intervention.create`, `edit_crimp`) | navigateur | règles par branche : auteur et statut ; soumission par l'API |
| 3 | Préventive 5 jours (validée sans labo) | A, SA, R, C | navigateur | API (photos et conformités obligatoires) |
| 3 | Import Komax des outils bloqués, clients et projets | A, SA, R, C | navigateur | API d'import ; écriture de `blockedTools` réservée au serveur |
| 3 | Import Dispatcheur Suivi | A, SA, R, C | navigateur (dédoublonnage dans le navigateur) | API d'import avec dédoublonnage serveur |
| 4 | Stock : import, commandes, mouvements | A, SA, R, M (`magasin.write`) | navigateur | règles par branche + API d'import |
| 4 | Répondre à une demande PDR | A, SA, M | navigateur | API (statut « en attente » obligatoire) |
| 4 | QRQC / PDCA | A, SA, R, C, L | navigateur | règles par branche |
| 4 | Documentation | A, SA, R, C, L, M, TL | navigateur | règles par branche |
| 4 | Planificateur d'actions | A, SA, R, TL | navigateur | règles par branche |
| 5 | Gestion des accès par rôle (`accessConfig`) | A, SA | navigateur | règles : A, SA seulement |
| 5 | Journal d'audit | tout compte (écriture) | navigateur | écrit par le serveur uniquement, jamais modifiable |
| 5 | Notifications vers un autre utilisateur | tout compte | navigateur | écrites par le serveur |

Une opération migrée vers l'API voit ensuite son écriture directe interdite par les règles, pour qu'on ne puisse pas contourner le serveur. L'application est mise à jour avant ce durcissement.
