package com.leoni.crimpingpro.users;

import com.leoni.crimpingpro.audit.AuditService;
import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.Permission;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.security.PrincipalResolver;
import com.leoni.crimpingpro.security.Role;
import com.leoni.crimpingpro.security.Uids;
import com.leoni.crimpingpro.security.UserDirectory;
import com.leoni.crimpingpro.security.UserProfile;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Administration des comptes. Le SDK Admin contourne les règles de la base : chaque règle est
 * donc vérifiée ici, explicitement, avant toute écriture. Elles reprennent les règles de la base
 * (database.rules.json) pour que l'API et l'accès direct se comportent pareil.
 */
@Service
public class UserAdminService {

  private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
  static final int PASSWORD_MIN = 8;
  static final int PASSWORD_MAX = 128;

  /** Données d'un nouveau compte. Le mot de passe ne va qu'à Firebase Authentication. */
  public record NewUser(String email, String password, String displayName, String matricule, String role) {
    @Override
    public String toString() {
      return "NewUser[email=" + email + ", displayName=" + displayName + ", matricule=" + matricule
          + ", role=" + role + ", password=***]";
    }
  }

  private final UserDirectory directory;
  private final AuditService audit;
  private final PermissionService permissions;
  private final PrincipalResolver resolver;
  private final Clock clock;

  public UserAdminService(UserDirectory directory, AuditService audit, PermissionService permissions,
      PrincipalResolver resolver, Clock clock) {
    this.directory = directory;
    this.audit = audit;
    this.permissions = permissions;
    this.resolver = resolver;
    this.clock = clock;
  }

  public String createUser(AppPrincipal actor, NewUser user) {
    require(actor, Permission.USERS_MANAGE);
    Role role = parseRole(user.role());
    if (role == Role.SUPER_ADMIN && actor.role() != Role.SUPER_ADMIN) {
      throw ApiException.forbidden("Seul un super administrateur peut créer un super administrateur.");
    }
    checkPassword(user.password());
    String email = user.email().trim();
    String uid;
    try {
      uid = directory.createAuthUser(email, user.password(), user.displayName().trim());
    } catch (UserDirectory.ConflictException e) {
      throw ApiException.conflict("Un compte existe déjà avec cet e-mail.");
    }
    long now = clock.millis();
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("uid", uid);
    profile.put("displayName", user.displayName().trim());
    profile.put("matricule", user.matricule().trim());
    profile.put("email", email);
    profile.put("role", role.code());
    profile.put("active", true);
    profile.put("createdAt", now);
    profile.put("createdBy", actor.label());
    profile.put("lastLoginAt", 0);
    profile.put("passwordChangedAt", now);
    profile.put("passwordChangedBy", actor.label());
    try {
      directory.writeProfile(uid, profile);
      directory.setRoleClaim(uid, role.code());
    } catch (RuntimeException e) {
      // Pas de compte à moitié créé : on supprime le compte d'authentification
      try {
        directory.deleteAuthUser(uid);
      } catch (RuntimeException cleanup) {
        log.error("Compte {} créé mais profil absent : suppression impossible", uid, cleanup);
      }
      throw e;
    }
    audit.record(actor, "user.create", "users/" + uid, Map.of("role", role.code(), "email", email));
    return uid;
  }

  public void changeRole(AppPrincipal actor, String targetUid, String roleCode) {
    require(actor, Permission.USERS_MANAGE);
    if (actor.uid().equals(targetUid)) {
      throw ApiException.conflict("Vous ne pouvez pas modifier votre propre rôle.");
    }
    Role newRole = parseRole(roleCode);
    UserProfile target = find(targetUid);
    Role current = Role.fromCode(target.role()).orElse(null);
    if ((newRole == Role.SUPER_ADMIN || current == Role.SUPER_ADMIN) && actor.role() != Role.SUPER_ADMIN) {
      throw ApiException.forbidden(
          "Seul un super administrateur peut attribuer ou retirer le rôle super administrateur.");
    }
    directory.writeProfile(targetUid, Map.of("role", newRole.code(), "updatedAt", clock.millis()));
    directory.setRoleClaim(targetUid, newRole.code());
    resolver.invalidate(targetUid);
    audit.record(actor, "user.role", "users/" + targetUid,
        Map.of("from", current == null ? "" : current.code(), "to", newRole.code()));
  }

  public void setActive(AppPrincipal actor, String targetUid, boolean active) {
    require(actor, Permission.USERS_MANAGE);
    if (actor.uid().equals(targetUid)) {
      throw ApiException.conflict("Vous ne pouvez pas activer ou désactiver votre propre compte.");
    }
    UserProfile target = find(targetUid);
    protectSuperAdmin(actor, target);
    directory.writeProfile(targetUid, Map.of("active", active, "updatedAt", clock.millis()));
    directory.setDisabled(targetUid, !active);
    if (!active) {
      directory.revokeSessions(targetUid);
    }
    resolver.invalidate(targetUid);
    audit.record(actor, "user.active", "users/" + targetUid, Map.of("active", active));
  }

  /** Vraie réinitialisation : change le mot de passe Firebase et met fin à toutes les sessions. */
  public void resetPassword(AppPrincipal actor, String targetUid, String password) {
    require(actor, Permission.USERS_MANAGE);
    if (actor.uid().equals(targetUid)) {
      throw ApiException.conflict(
          "Pour votre propre mot de passe, utilisez « Changer mot de passe » dans votre profil.");
    }
    UserProfile target = find(targetUid);
    protectSuperAdmin(actor, target);
    checkPassword(password);
    directory.setPassword(targetUid, password);
    directory.revokeSessions(targetUid);
    directory.writeProfile(targetUid,
        Map.of("passwordChangedAt", clock.millis(), "passwordChangedBy", actor.label()));
    audit.record(actor, "user.passwordReset", "users/" + targetUid, Map.of());
  }

  public void revokeSessions(AppPrincipal actor, String targetUid) {
    require(actor, Permission.SESSIONS_REVOKE);
    UserProfile target = find(targetUid);
    if (!actor.uid().equals(targetUid)) {
      protectSuperAdmin(actor, target);
    }
    directory.revokeSessions(targetUid);
    audit.record(actor, "user.revokeSessions", "users/" + targetUid, Map.of());
  }

  private void require(AppPrincipal actor, Permission permission) {
    if (!permissions.has(actor, permission)) {
      throw ApiException.forbidden("Action réservée aux administrateurs.");
    }
  }

  private UserProfile find(String uid) {
    if (!Uids.isValid(uid)) {
      throw ApiException.badRequest("Identifiant d'utilisateur invalide.");
    }
    return directory.findProfile(uid)
        .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable."));
  }

  private static Role parseRole(String code) {
    return Role.fromCode(code == null ? null : code.trim())
        .orElseThrow(() -> ApiException.badRequest("Rôle inconnu."));
  }

  private static void protectSuperAdmin(AppPrincipal actor, UserProfile target) {
    if ("super_admin".equals(target.role()) && actor.role() != Role.SUPER_ADMIN) {
      throw ApiException.forbidden("Seul un super administrateur peut agir sur ce compte.");
    }
  }

  private static void checkPassword(String password) {
    if (password == null || password.isBlank()
        || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
      throw ApiException.badRequest("Le mot de passe doit faire entre 8 et 128 caractères.");
    }
  }
}
