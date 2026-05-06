# 🔧 LEONI Crimping Lab — Version Unifiée

## 📦 Architecture simplifiée

Cette version utilise **un seul fichier** pour éviter les bugs de cache et de versions :

| Fichier | Rôle | Obligatoire ? |
|---|---|---|
| `index.html` | **TOUT en un** : HTML + CSS + JS + Login + Dashboard | ✅ Oui |
| `database.rules.json` | Règles de sécurité Firebase | ✅ Oui (à publier dans Firebase Console) |

C'est tout ! Plus de `app.js`, plus de `styles.css`, plus de `admin-dashboard.html` séparés.

---

## 🚀 Déploiement sur GitHub Pages

### Option A : Garder uniquement les 2 fichiers nécessaires
1. Sur ton repo GitHub `CRIMPING-LABO-2`
2. **Supprime** tous les anciens fichiers :
   - `admin-dashboard.html`
   - `app.js`
   - `styles.css`
   - `sw.js`
   - `auth-guard.js`
   - `crimp-dashboard.html`
   - `dashboard-examples.html`
   - `electron-storage.js`
   - `executive-dashboard.html`
   - `firebase-init.js`
   - `firestore.rules`
   - `init-loginindex.html`
   - `labo-dashboard.html`
   - `login.js`
   - `main.js`
   - `master-admin-setup.html`
   - `repair-admin.html`
3. **Garde uniquement** :
   - `index.html` (cette nouvelle version)
   - `database.rules.json`
   - `README.md`

### Option B : Renommer index.html
Si tu préfères garder `admin-dashboard.html` comme nom :
1. Renomme `index.html` → `admin-dashboard.html` sur GitHub
2. Supprime tous les autres fichiers

---

## 🔐 Connexion par PIN

### Comptes par défaut

| Nom | Matricule | PIN |
|---|---|---|
| SUPER ADMIN | 10541 | 1997 |
| CRIMPING | 4 | 4567 |
| magasinier | 2 | 5432 |
| laboratoire | 3 | 2345 |

### Comment se connecter
1. Aller sur `https://maintenance-pro.github.io/CRIMPING-LABO-2/`
2. Taper le PIN → connexion automatique au 4ème chiffre

---

## 🛠️ Modifier un PIN

1. Connecte-toi en SUPER ADMIN
2. Aller dans **Utilisateurs**
3. **Cliquer sur le PIN** (badge cyan) dans la table
4. Saisir le nouveau PIN à 4 chiffres
5. ✅ Effectif immédiatement

---

## ➕ Ajouter un utilisateur

1. **Utilisateurs** → **➕ Nouvel utilisateur**
2. Remplir Nom + Matricule + Rôle
3. Cliquer **🎲 Générer** pour un PIN aléatoire (ou taper le tien)
4. Email et mot de passe technique sont auto-générés (ou tu peux les définir dans "Paramètres avancés")
5. **✓ Créer l'utilisateur**

---

## 🐛 En cas de bug

1. **Ctrl+Shift+R** dans le navigateur (vider cache)
2. Si ça persiste : F12 → **Application** → **Storage** → **Clear site data**

Vu qu'il n'y a plus qu'un seul fichier, **plus de problèmes de cache** entre fichiers.
