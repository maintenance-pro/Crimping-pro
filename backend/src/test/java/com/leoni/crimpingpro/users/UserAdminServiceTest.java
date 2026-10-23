package com.leoni.crimpingpro.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.security.PrincipalResolver;
import com.leoni.crimpingpro.security.Role;
import com.leoni.crimpingpro.support.InMemoryUserDirectory;
import com.leoni.crimpingpro.support.RecordingAuditService;
import com.leoni.crimpingpro.users.UserAdminService.NewUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserAdminServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneOffset.UTC);
  private InMemoryUserDirectory directory;
  private RecordingAuditService audit;
  private UserAdminService service;

  private final AppPrincipal superAdmin = new AppPrincipal("sa", "sa@x", Role.SUPER_ADMIN, true, "Super", null);
  private final AppPrincipal admin = new AppPrincipal("admin1", "a@x", Role.ADMIN, true, "Admin", null);
  private final AppPrincipal crimp = new AppPrincipal("crimp1", "c@x", Role.CRIMP, true, "Tech", null);

  @BeforeEach
  void setUp() {
    directory = new InMemoryUserDirectory();
    audit = new RecordingAuditService();
    directory.seed("sa", "super_admin", true);
    directory.seed("admin1", "admin", true);
    directory.seed("crimp1", "crimp", true);
    directory.seed("labo1", "labo", true);
    service = new UserAdminService(directory, audit, new PermissionService(),
        new PrincipalResolver(directory, clock), clock);
  }

  private static HttpStatus statusOf(Throwable t) {
    return ((ApiException) t).status();
  }

  @Test
  void creation_ne_stocke_aucun_secret_et_trace_l_auteur() {
    String uid = service.createUser(admin, new NewUser("n@leoni.com", "motdepasse1", "Nouveau", "123", "crimp"));

    assertThat(directory.profiles.get(uid)).doesNotContainKeys("password", "pin")
        .containsEntry("role", "crimp").containsEntry("active", true).containsEntry("createdBy", "Admin");
    assertThat(directory.roleClaims).containsEntry(uid, "crimp");
    assertThat(audit.entries).singleElement().satisfies(e -> {
      assertThat(e.actorUid()).isEqualTo("admin1");
      assertThat(e.action()).isEqualTo("user.create");
      assertThat(e.details().values()).doesNotContain("motdepasse1");
    });
  }

  @Test
  void creation_annulee_si_le_profil_ne_peut_pas_etre_ecrit() {
    directory.failNextProfileWrite = true;
    assertThatThrownBy(() -> service.createUser(admin,
        new NewUser("n@leoni.com", "motdepasse1", "Nouveau", "123", "crimp")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(directory.deletedAuth).hasSize(1);
    assertThat(audit.entries).isEmpty();
  }

  @Test
  void creation_refusee_pour_un_non_admin_un_mot_de_passe_court_ou_un_email_existant() {
    assertThatThrownBy(() -> service.createUser(crimp, new NewUser("n@x", "motdepasse1", "N", "1", "crimp")))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.createUser(admin, new NewUser("n@x", "court", "N", "1", "crimp")))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> service.createUser(admin, new NewUser("admin1@leoni.test", "motdepasse1", "N", "1", "crimp")))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void seul_un_super_admin_cree_un_super_admin() {
    assertThatThrownBy(() -> service.createUser(admin, new NewUser("n@x", "motdepasse1", "N", "1", "super_admin")))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThat(service.createUser(superAdmin, new NewUser("n@x", "motdepasse1", "N", "1", "super_admin"))).isNotBlank();
  }

  @Test
  void changer_son_propre_role_est_refuse() {
    assertThatThrownBy(() -> service.changeRole(admin, "admin1", "super_admin"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
    assertThat(directory.profiles.get("admin1")).containsEntry("role", "admin");
  }

  @Test
  void un_admin_change_le_role_d_un_autre_et_le_jeton_suit() {
    service.changeRole(admin, "crimp1", "labo");
    assertThat(directory.profiles.get("crimp1")).containsEntry("role", "labo");
    assertThat(directory.roleClaims).containsEntry("crimp1", "labo");
    assertThat(audit.entries).singleElement()
        .satisfies(e -> assertThat(e.details()).containsEntry("from", "crimp").containsEntry("to", "labo"));
  }

  @Test
  void super_admin_protege() {
    assertThatThrownBy(() -> service.changeRole(admin, "crimp1", "super_admin"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.changeRole(admin, "sa", "crimp"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.setActive(admin, "sa", false))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.resetPassword(admin, "sa", "nouveaumdp1"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void role_inconnu_ou_utilisateur_absent() {
    assertThatThrownBy(() -> service.changeRole(admin, "crimp1", "chef_supreme"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> service.changeRole(admin, "fantome", "labo"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> service.changeRole(admin, "x/role", "labo"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void desactivation_coupe_les_sessions_et_le_compte() {
    service.setActive(admin, "crimp1", false);
    assertThat(directory.profiles.get("crimp1")).containsEntry("active", false);
    assertThat(directory.disabled).contains("crimp1");
    assertThat(directory.revoked).contains("crimp1");

    assertThatThrownBy(() -> service.setActive(admin, "admin1", false))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void reinitialisation_change_le_vrai_mot_de_passe_revoque_et_ne_trace_aucun_secret() {
    service.resetPassword(admin, "labo1", "nouveaumdp1");
    assertThat(directory.passwordsSet).containsEntry("labo1", "nouveaumdp1");
    assertThat(directory.revoked).contains("labo1");
    assertThat(directory.profiles.get("labo1")).doesNotContainKeys("password", "pin");
    assertThat(audit.entries).singleElement()
        .satisfies(e -> assertThat(e.details()).isEmpty());

    assertThatThrownBy(() -> service.resetPassword(admin, "admin1", "nouveaumdp1"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void un_technicien_ne_peut_rien_administrer() {
    assertThatThrownBy(() -> service.changeRole(crimp, "labo1", "admin"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.revokeSessions(crimp, "labo1"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
  }
}
