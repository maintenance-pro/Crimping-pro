# Formule Blaze — estimation avant activation

**À lire avant d'activer quoi que ce soit.**
- Le coût nul n'est pas garanti.
- Une alerte de budget **ne bloque pas** les dépenses : elle envoie seulement un e-mail quand un seuil est atteint, souvent avec quelques heures de décalage, et la facturation continue.
- Rien n'a été activé ni déployé.

Tarifs relevés le 23/10/2026, en dollars US. La facture est émise dans la devise du compte de facturation.

Sources :
- Firebase : <https://firebase.google.com/pricing>, page lue directement.
- Cloud Run, Secret Manager, Artifact Registry : <https://cloud.google.com/run/pricing>, <https://cloud.google.com/secret-manager/pricing>, <https://cloud.google.com/artifact-registry/pricing>. Chiffres repris des extraits de recherche des pages officielles, car la page Cloud Run n'a pas pu être lue en entier. **Revérifiez-les** sur ces pages, ou dans le simulateur <https://cloud.google.com/products/calculator>, juste avant d'activer.

## Ce qui change en passant de Spark à Blaze

- Les quotas gratuits de Spark restent inclus sur Blaze. Mais pour la Realtime Database, **la part gratuite de Blaze est calculée par jour** : 360 Mo téléchargés par jour, contre 10 Go par mois sur Spark. Un jour chargé est facturé même si le mois reste sous 10 Go.
- Sur Spark, un dépassement est bloqué. **Sur Blaze, il est facturé.**
- Cloud Run, Secret Manager et Artifact Registry demandent Blaze.

## Tarifs utiles

| Service | Gratuit | Au-delà |
|---|---|---|
| Realtime Database — données téléchargées | 360 Mo par jour | 1 $ par Go |
| Realtime Database — données stockées | 1 Go | 5 $ par Go et par mois |
| Authentication (e-mail, jeton personnalisé du PIN) | 50 000 utilisateurs actifs par mois | sans objet ici |
| Cloud Run, facturation à la requête (région de niveau 1, ex. europe-west1) | par mois : 180 000 vCPU-s, 360 000 Gio-s, 2 millions de requêtes | 0,000024 $ / vCPU-s, 0,0000025 $ / Gio-s, 0,40 $ / million de requêtes |
| Secret Manager (secret `PIN_PEPPER`) | 6 versions actives, 10 000 accès par mois | 0,06 $ par version, 0,03 $ par 10 000 accès |
| Artifact Registry (image du serveur, environ 0,3 Go) | 0,5 Go | 0,10 $ par Go et par mois |
| Cloud Build (construction de l'image) | quota gratuit mensuel | à vérifier sur la page de tarifs |
| Cloud Storage for Firebase | quotas gratuits **seulement pour les buckets en us-central1, us-west1, us-east1** | voir plus bas |

## Usage retenu pour l'estimation du serveur

À ajuster avec vos chiffres réels.

- Une dizaine de comptes, deux ou trois équipes.
- 30 à 60 décisions labo par jour.
- Jusqu'à 50 connexions par PIN par jour, une dizaine d'opérations d'administration.
- Environ 150 appels au serveur par jour, soit 4 500 par mois.
- Serveur : 1 vCPU et 512 Mio par instance, `min-instances=0` (aucune instance permanente).

| Poste | Calcul | Résultat |
|---|---|---|
| Requêtes | 4 500 par mois, pour 2 millions gratuits | 0 $ |
| CPU des requêtes | 4 500 × 0,5 s ≈ 2 250 vCPU-s | 0 $ |
| CPU des démarrages à froid (Java) | jusqu'à 30 démarrages par jour × 8 s ≈ 7 200 vCPU-s par mois | 0 $ |
| Total CPU | ≈ 9 500 vCPU-s, environ 5 % des 180 000 gratuits | 0 $ |
| Mémoire | 9 500 s × 0,5 Gio ≈ 4 750 Gio-s, environ 1 % des 360 000 gratuits | 0 $ |
| Secret Manager | 1 secret, lu au démarrage des instances | 0 $ |
| Artifact Registry | 1 ou 2 images conservées, < 0,5 Go | 0 $ (supprimez les anciennes images) |

Même avec un usage multiplié par dix, Cloud Run reste dans la part gratuite.

**Option `min-instances=1`** : elle supprime l'attente de quelques secondes au premier appel après une pause. En contrepartie, une instance est facturée **en continu, même sans requête** : de quelques dollars à une quinzaine par mois selon la CPU et la mémoire. Calculez-la dans le simulateur avant de l'activer. Elle n'est pas recommandée au départ.

## Le vrai risque : les téléchargements de la Realtime Database

Le serveur ajoute peu de téléchargements : chaque décision relit le bon, photos comprises, soit environ 0,5 à 3 Mo. 30 à 60 décisions par jour représentent 15 à 180 Mo par jour.

Mais **l'application elle-même** télécharge déjà beaucoup :
- elle écoute des branches entières en temps réel (bons, outils, stock…) ;
- les photos sont stockées en base64 dans la base ;
- chaque connexion d'un poste recharge ces branches.

Ces volumes existent aujourd'hui, mais ils ne sont pas visibles d'ici : je n'ai pas accès à vos statistiques.

Coût mensuel de la base ≈ somme, jour par jour, de max(0 ; Go téléchargés ce jour − 0,36) × 1 $, plus max(0 ; Go stockés − 1) × 5 $.

| Téléchargement moyen par jour | Coût mensuel des téléchargements |
|---|---|
| 0,3 Go | 0 $ |
| 0,5 Go | ≈ 4 $ |
| 1 Go | ≈ 19 $ |
| 3 Go | ≈ 79 $ |
| 10 Go | ≈ 290 $ |

**À relever avant d'activer Blaze**, dans la console Firebase → Realtime Database → onglet **Utilisation** :
1. Les **téléchargements par jour** sur 30 jours : la valeur la plus haute compte autant que la moyenne.
2. Le **stockage** total : au-delà de 1 Go, 5 $ par Go et par mois.
3. Dans Storage, l'**emplacement du bucket**. S'il n'est pas en us-central1, us-west1 ou us-east1, son usage est facturé dès le premier octet sur Blaze. L'application y range les signatures et certaines photos : les montants sont faibles, mais pas nuls.

Si les téléchargements dépassent déjà 360 Mo par jour, Blaze coûtera chaque mois au moins le montant du tableau, serveur ou pas. Ces pistes de réduction sont proposées pour plus tard ; aucune n'est faite :
- ranger les photos dans Storage ;
- limiter les écouteurs aux bons récents ;
- ne plus recharger toute la base à chaque connexion.

## Estimation globale

| Élément | Estimation mensuelle |
|---|---|
| Serveur (Cloud Run, Secret Manager, Artifact Registry), `min-instances=0` | 0 $ attendu dans l'usage ci-dessus. Pas garanti : un usage anormal, une boucle d'appels ou des images non nettoyées peuvent coûter. |
| Realtime Database | 0 $ si les téléchargements restent sous 360 Mo **chaque jour** et le stockage sous 1 Go ; sinon selon le tableau ci-dessus |
| Authentication | 0 $ (moins de 50 000 utilisateurs actifs) |
| Cloud Storage | 0 $ ou quelques centimes selon l'emplacement du bucket |

## Garde-fous de facturation

1. **Budget** : console Google Cloud → Facturation → Budgets et alertes. Par exemple 10 €, avec alertes à 50 %, 90 % et 100 %, et une seconde alerte à 25 €. Destinataires : l'administrateur et le responsable maintenance.
   - **Ces alertes ne coupent rien.** La consommation et la facture continuent au-delà du budget.
   - Couper automatiquement est possible (alerte, puis Pub/Sub, puis fonction qui retire la facturation), mais cela **arrête aussi la base et l'application** : déconseillé pour un outil de production.
2. **Suivi quotidien la première semaine** : onglet Utilisation de la Realtime Database, et Facturation → Rapports.
3. **Cloud Run** : `--max-instances 2` dans la commande de déploiement, pour plafonner le nombre d'instances en cas de pic.
4. **Artifact Registry** : règle de nettoyage qui ne garde que les 2 dernières images.
