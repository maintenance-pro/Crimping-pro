package com.leoni.crimpingpro.security;

/**
 * Permissions vérifiées par le serveur. La matrice complète, y compris les opérations qui
 * seront migrées ensuite, est dans docs/matrice-permissions.md.
 */
public enum Permission {
  /** Créer un compte, changer un rôle, activer ou désactiver un compte, réinitialiser un mot de passe. */
  USERS_MANAGE,
  /** Mettre fin aux sessions d'un compte. */
  SESSIONS_REVOKE,
  /** Décision laboratoire : valider ou refuser un bon, livrer le dossier (intervention.validate / reject). */
  INTERVENTION_DECIDE,
  /** Validation automatique d'un bon « réglage mécanique seul » à la soumission (intervention.create). */
  INTERVENTION_AUTO_VALIDATE
}
