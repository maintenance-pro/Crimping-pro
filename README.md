# Crimping Pro

## Présentation

Crimping Pro est une application web Progressive Web App (PWA) dédiée à la gestion des interventions de sertissage et à la validation en laboratoire pour LEONI Wiring Systems.

L’application permet de :
- créer et suivre des bons d’intervention,
- saisir les données techniques liées au sertissage,
- gérer les validations laboratoire,
- consulter un historique des interventions,
- utiliser l’interface sur desktop ou mobile.

## Fonctionnalités principales

- Interface moderne et responsive
- Gestion des bons d’intervention
- Formulaires de saisie pour les étapes de crimping et de validation
- Historique et suivi des interventions
- Support PWA avec installation locale
- Intégration possible avec Firebase pour l’authentification, la base de données et le stockage

## Structure du projet

L’application est centralisée dans un unique fichier `index.html` : HTML, CSS et
JavaScript (y compris la configuration Firebase et l’outil de réparation du
compte admin) y sont regroupés pour former une Single Page Application autonome.

- index.html : application complète (SPA) — vues, styles, logique et config Firebase
- manifest.json : configuration de la PWA
- sw.js : service worker pour le fonctionnement hors ligne
- database.rules.json : règles de sécurité pour la base de données
- assets/ : icônes de l’application (icon-192.png, icon-512.png, icon-512-maskable.png)

L’outil de réparation du compte admin (anciennement `repair-admin.html`) est
désormais accessible directement depuis l’écran de connexion via le lien
« 🔧 Compte admin non reconnu ? Outil de réparation ».

## Prérequis

- Un navigateur moderne
- Un serveur web local ou un hébergement statique
- Éventuellement un projet Firebase configuré si vous souhaitez utiliser les services backend

## Démarrage local

Vous pouvez ouvrir l’application directement dans un navigateur en ouvrant le fichier index.html.

Pour un démarrage plus proche d’un environnement web, vous pouvez aussi utiliser un simple serveur local, par exemple :

```bash
python -m http.server 8000
```

Puis ouvrir :

```text
http://localhost:8000
```

## Déploiement

Ce projet peut être déployé sur :
- GitHub Pages
- Netlify
- Firebase Hosting
- tout autre hébergement statique

Assurez-vous de publier les fichiers principaux à la racine du site, notamment :
- index.html
- manifest.json
- sw.js
- les icônes et autres ressources associées

## Notes importantes

Le projet semble être une application front-end PWA et peut dépendre de services externes comme Firebase pour certaines fonctionnalités. Si vous souhaitez l’utiliser en production, il est recommandé de vérifier et configurer les identifiants et règles d’accès avant déploiement.

## Auteur

Projet LEONI Maintenance Pro — Crimping Lab.

