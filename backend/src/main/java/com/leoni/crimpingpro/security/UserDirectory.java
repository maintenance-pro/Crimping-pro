package com.leoni.crimpingpro.security;

import java.util.Map;
import java.util.Optional;

/**
 * Comptes Firebase Authentication et profils users/{uid}. Seul le serveur l'utilise.
 * Les mots de passe ne sont transmis qu'à Firebase Authentication, jamais stockés ailleurs.
 */
public interface UserDirectory {

  Optional<UserProfile> findProfile(String uid);

  /** Crée le compte d'authentification et renvoie son UID. */
  String createAuthUser(String email, String password, String displayName);

  /** Met à jour (fusionne) des champs du profil. Refuse tout champ password ou pin. */
  void writeProfile(String uid, Map<String, Object> fields);

  /** Recopie le rôle dans les custom claims du jeton (utilisables plus tard par les règles). */
  void setRoleClaim(String uid, String role);

  void setPassword(String uid, String password);

  void setDisabled(String uid, boolean disabled);

  /** Met fin à toutes les sessions : les jetons existants sont refusés (vérification checkRevoked). */
  void revokeSessions(String uid);

  /** Annule une création dont la suite a échoué. */
  void deleteAuthUser(String uid);

  /** Compte déjà existant pour cet e-mail. */
  class ConflictException extends RuntimeException {
    public ConflictException() {
      super("Compte déjà existant");
    }
  }
}
