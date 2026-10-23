package com.leoni.crimpingpro.security;

import org.springframework.stereotype.Component;

/**
 * Matrice des droits côté serveur. Reprend les droits de l'application actuelle (auth.can) :
 * <ul>
 *   <li>gestion des utilisateurs : admin, super_admin ;
 *   <li>intervention.validate / reject (décision labo) : admin, super_admin, responsable, labo ;
 *   <li>intervention.create (soumission, donc validation automatique) : admin, super_admin,
 *       responsable, crimp, crimping.
 * </ul>
 */
@Component
public class PermissionService {

  public boolean has(AppPrincipal principal, Permission permission) {
    if (principal == null || !principal.approved()) {
      return false;
    }
    Role role = principal.role();
    return switch (permission) {
      case USERS_MANAGE, SESSIONS_REVOKE -> role.isAdmin();
      case INTERVENTION_DECIDE -> role.isAdmin() || role == Role.RESPONSABLE || role == Role.LABO;
      case INTERVENTION_AUTO_VALIDATE -> role.isAdmin() || role == Role.RESPONSABLE
          || role == Role.CRIMP || role == Role.CRIMPING;
    };
  }
}
