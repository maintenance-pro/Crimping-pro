# Passage des décisions sur les bons par le serveur

Objectif : la validation, le refus, la livraison du dossier et la validation automatique ne sont plus écrits par le navigateur, mais par le serveur, en une seule transaction.

Contraintes :
- aucune perte ;
- aucune double opération ;
- mêmes données Firebase ;
- même comportement métier.

## Ce qui est en place dans cette branche

| Élément | Où |
|---|---|
| **API des décisions**. Chacune est une transaction sur le bon : l'état est relu et revérifié au moment d'écrire. De deux décisions simultanées, une seule passe ; l'autre reçoit 409. | `POST /api/interventions/{n°}/validate`, `/reject`, `/deliver`, `/auto-validate` |
| **Identifiant de requête** (`requestId`). Le navigateur le choisit pour une décision et le réutilise s'il réessaie. Le serveur le garde dans l'historique du bon : un nouvel essai renvoie « déjà appliquée » au lieu d'écrire une seconde fois. | serveur + `api.decide()` dans `index.html` |
| **Suites de la décision écrites à des clés déterministes**. Un nouvel essai complète ce qui manque, sans doublon. Suites concernées : audit, notification à l'auteur du bon, déblocage de l'outil pour la validation automatique. | `LabDecisionService` |
| **Enregistrement PDR une seule fois par bon** : verrou `interventions/{n°}/pdrAutoRecord` posé par transaction. Protège aussi le chemin actuel, sans serveur. | `wearAlerts.recordPdrChangeFromBon` |
| **Contrôle des fichiers joints** : images ou PDF uniquement, contenu conforme au type annoncé, 7 Mo au plus. | `DataUrls` |
| **Bascule** : `API_BASE_URL` vide = comportement actuel inchangé. Rempli = les quatre décisions passent par le serveur. | `index.html` |
| **Bandeau « nouvelle version »** pour les onglets restés ouverts. Il s'affiche si `system/clientPolicy/minBuild` > `APP_BUILD` de l'onglet. Non bloquant : l'utilisateur termine sa saisie puis recharge. | `clientPolicy` dans `index.html` |
| **Service worker** : ne met jamais en cache une réponse du serveur ni une requête authentifiée. | `sw.js` |

## Ordre d'activation

Chaque étape est réversible jusqu'à la publication des règles de la phase 2.

| Étape | Action | Effet pour les utilisateurs |
|---|---|---|
| A | Publier les règles de la phase 1 (`docs/regles-firebase.md`). | Aucun pour les comptes approuvés. |
| B | Déployer cette version de l'application **avec `API_BASE_URL` vide**. | Aucun changement visible. Le bandeau de version et l'anti-doublon PDR sont présents. Chaque poste recharge une fois (Ctrl+F5) au changement d'équipe. |
| C | Activer Blaze (après validation de `docs/estimation-blaze.md`), déployer le serveur sur Cloud Run, vérifier `/actuator/health`. | Aucun : l'application n'appelle pas encore le serveur. |
| D | Mettre l'adresse Cloud Run dans `API_BASE_URL` et augmenter `APP_BUILD`, déployer. Puis, dans la console (Realtime Database → Données), créer `system/clientPolicy/minBuild` = la valeur de `APP_BUILD`. | Les onglets de l'étape B affichent le bandeau et rechargent. Les onglets plus anciens que l'étape B n'ont pas le bandeau : demander à chacun de recharger (F5). |
| E | Quelques jours d'observation : décisions faites, aucune erreur serveur (journaux Cloud Run). | Les deux chemins coexistent (voir ci-dessous). |
| F | Publier `database.rules.phase2.json`. | Un onglet ancien ne peut plus décider : « permission refusée », rien n'est écrit, le bon reste en attente. |
| G | PIN : secret `PIN_PEPPER` dans Secret Manager, enrôlement des postes, définition des PIN. **Seulement après l'étape F**, pour que la branche `secure/` soit déjà illisible depuis les navigateurs. | Connexion par PIN proposée sur les postes enrôlés. |

## Anciens onglets

Le service worker charge toujours la dernière version quand le réseau est disponible : un rechargement suffit.

| Situation | Entre les étapes D et F | Après l'étape F |
|---|---|---|
| Un ancien onglet décide sur un bon **déjà décidé** par le serveur | Refusé par le contrôle de statut de l'ancien code (relecture du bon). Il reste une fenêtre de moins d'une seconde où l'ancien code peut écraser la décision. | Refusé par les règles. |
| Un ancien onglet décide sur un bon **pas encore décidé** | Appliqué par l'ancien chemin, avec les mêmes champs qu'aujourd'hui. | Refusé. Message d'erreur, rien n'est écrit, le bon reste dans la file du labo. |
| Un ancien onglet fait une autre saisie (brouillon, édition, stock…) | Inchangé. | Inchangé. |

La fenêtre résiduelle entre D et F est la raison de publier F dès que tous les postes ont rechargé.

## Écritures hors ligne

| Cas | Nouvelle version (`API_BASE_URL` rempli) | Ancienne version |
|---|---|---|
| Décision sans réseau | **Rien n'est mis en attente.** Message : « Serveur injoignable : rien n'a été enregistré ». Le bon reste en attente ; l'utilisateur réessaie quand le réseau revient. | La décision attend en mémoire dans l'onglet. Elle est perdue si l'onglet est fermé avant le retour du réseau. Envoyée à la reconnexion : appliquée avant l'étape F, refusée après (le bon reste en attente, visible). |
| Réponse perdue (délai dépassé) | Message : « l'opération a peut-être été enregistrée. Réessayez, elle ne sera pas appliquée deux fois ». Le nouvel essai réutilise le même `requestId` : le serveur répond « déjà appliquée ». | Double validation possible (cas connu de l'ancien code). |
| Double clic, deux onglets, deux laborantins en même temps | Une seule décision appliquée, l'autre reçoit 409 « Ce bon a déjà été validé — rechargez la fiche ». | Double écriture possible. |
| Brouillon, édition d'un bon, autres saisies | Inchangé : écriture directe dans Firebase, mise en attente hors ligne comme aujourd'hui. | Inchangé. |

**Aucune perte silencieuse** :
- Une décision est soit enregistrée et confirmée, soit refusée avec un message.
- Dans les deux cas, le bon reste dans un état visible : validé, ou toujours en attente.

## Écarts avec le comportement actuel, et pourquoi

| Écart | Raison |
|---|---|
| Un dossier **livré** ne peut plus être refusé. | L'application annonce la livraison comme « définitive et irréversible ». L'ancien code ne le vérifiait pas. |
| Le message de notification « dossier livré » est corrigé. | L'ancien code envoyait « Ton bon N°… a été refusé : null » à la livraison. |
| Une coupe ou un rapport déjà joints sont conservés si aucun nouveau fichier n'est envoyé. | L'ancien code les effaçait à la validation ou à la livraison. |
| Validation automatique : un outil absent du catalogue n'est pas créé. | L'ancien code créait une fiche d'outil partielle. |
| `crimping.reglageMecanique` est enregistré dès la création du bon. | L'ancien code ne l'enregistrait qu'en modification. Le serveur en a besoin pour revérifier la condition « réglage mécanique seul ». |
| Fichiers joints : images ou PDF, 7 Mo au plus, contenu vérifié. | Au-delà de 7 Mo environ, l'enregistrement échouait déjà (limite de 10 Mo par texte dans la base). Le contrôle empêche aussi d'enregistrer une page web déguisée en image. |
| Une décision demande le réseau. | C'est ce qui évite les doubles décisions et les décisions perdues dans un onglet fermé. |
| Si le mode TEST/PROD a changé entre l'affichage et la décision, la décision est refusée (409, « rechargez »). | Évite d'écrire dans le mauvais espace de données. |

## Retour arrière

- **Avant l'étape F** : remettre `API_BASE_URL = ''` et redéployer l'application. Les décisions reprennent l'ancien chemin. Le serveur peut être arrêté sans perte : il n'est pas la source des données.
- **Après l'étape F** : republier d'abord `database.rules.json` (phase 1, toujours sûre : pas d'accès public, pas de secrets), puis remettre `API_BASE_URL = ''`.
  - Si des PIN existent déjà (étape G), la phase 1 rend la branche `secure/` lisible par les comptes approuvés.
  - Ces empreintes restent inexploitables sans le secret du serveur, mais supprimez les PIN, ou republiez la phase 2 dès que possible.
- **Jamais** de retour à des règles publiques ou à des mots de passe en base.
