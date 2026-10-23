package com.leoni.crimpingpro.security;

/**
 * Identité de l'appelant, déduite UNIQUEMENT du jeton Firebase vérifié et du profil protégé.
 * Jamais d'un UID ou d'un rôle transmis dans le corps de la requête.
 *
 * @param role null si le compte n'a pas de profil validé par un administrateur
 * @param matricule matricule du profil (peut être absent)
 */
public record AppPrincipal(String uid, String email, Role role, boolean active, String displayName,
    String matricule) {

  /** Compte reconnu : profil avec un rôle connu, et non désactivé. */
  public boolean approved() {
    return role != null && active;
  }

  /** Nom affiché dans le journal d'audit. */
  public String label() {
    if (displayName != null && !displayName.isBlank()) {
      return displayName;
    }
    return email != null ? email : uid;
  }
}
