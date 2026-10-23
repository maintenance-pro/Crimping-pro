package com.leoni.crimpingpro.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leoni.crimpingpro.security.PrincipalResolver;
import com.leoni.crimpingpro.support.FakeTokenVerifier;
import com.leoni.crimpingpro.support.InMemoryUserDirectory;
import com.leoni.crimpingpro.support.RecordingAuditService;
import com.leoni.crimpingpro.support.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
    "app.firebase.enabled=false",
    "app.cors.allowed-origins=https://crimping.example",
    "app.rate-limit.admin-per-minute=100"
})
@Import(TestSupport.class)
class ApiSecurityTest {

  @Autowired WebApplicationContext context;
  @Autowired FakeTokenVerifier tokens;
  @Autowired InMemoryUserDirectory directory;
  @Autowired RecordingAuditService audit;
  @Autowired PrincipalResolver resolver;

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    directory.reset();
    audit.entries.clear();
    resolver.invalidateAll();
    directory.seed("sa", "super_admin", true);
    directory.seed("admin1", "admin", true);
    directory.seed("crimp1", "crimp", true);
    directory.seed("labo1", "labo", true);
    directory.seed("ancien", "crimp", false);
  }

  private String bearer(String uid) {
    return "Bearer " + tokens.tokenFor(uid);
  }

  // ── Vérification du jeton ────────────────────────────────────────────────────
  @Test
  void sans_jeton_401_avec_entete_bearer() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void jeton_invalide_expire_revoque_ou_compte_desactive_401() throws Exception {
    for (String t : new String[] {"n'importe-quoi", "expired", "revoked", "disabled"}) {
      mvc.perform(get("/api/me").header("Authorization", "Bearer " + t))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void compte_sans_profil_ou_desactive_dans_l_application_403() throws Exception {
    mvc.perform(get("/api/me").header("Authorization", bearer("inconnu")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/me").header("Authorization", bearer("ancien")))
        .andExpect(status().isForbidden());
  }

  @Test
  void identite_et_permissions_viennent_du_jeton_et_du_profil() throws Exception {
    mvc.perform(get("/api/me").header("Authorization", bearer("crimp1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uid").value("crimp1"))
        .andExpect(jsonPath("$.role").value("crimp"))
        .andExpect(jsonPath("$.permissions.length()").value(1))
        .andExpect(jsonPath("$.permissions[0]").value("INTERVENTION_AUTO_VALIDATE"));
    mvc.perform(get("/api/me").header("Authorization", bearer("labo1")))
        .andExpect(jsonPath("$.permissions.length()").value(1))
        .andExpect(jsonPath("$.permissions[0]").value("INTERVENTION_DECIDE"));
    mvc.perform(get("/api/me").header("Authorization", bearer("admin1")))
        .andExpect(jsonPath("$.permissions[0]").value("USERS_MANAGE"));
  }

  // ── Opérations d'administration ──────────────────────────────────────────────
  @Test
  void un_technicien_ne_peut_pas_changer_un_role() throws Exception {
    mvc.perform(put("/api/admin/users/labo1/role").header("Authorization", bearer("crimp1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"admin\"}"))
        .andExpect(status().isForbidden());
    assertThat(directory.profiles.get("labo1")).containsEntry("role", "labo");
  }

  @Test
  void l_auteur_vient_du_jeton_pas_du_corps_de_la_requete() throws Exception {
    mvc.perform(put("/api/admin/users/crimp1/role").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"labo\",\"uid\":\"sa\",\"actor\":\"sa\",\"by\":\"sa\"}"))
        .andExpect(status().isNoContent());
    assertThat(directory.profiles.get("crimp1")).containsEntry("role", "labo");
    assertThat(audit.entries).singleElement().satisfies(e -> assertThat(e.actorUid()).isEqualTo("admin1"));
  }

  @Test
  void changer_son_propre_role_409_et_super_admin_reserve_403() throws Exception {
    mvc.perform(put("/api/admin/users/admin1/role").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"super_admin\"}"))
        .andExpect(status().isConflict());
    mvc.perform(put("/api/admin/users/crimp1/role").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"super_admin\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void identifiant_qui_designerait_un_autre_noeud_400() throws Exception {
    mvc.perform(put("/api/admin/users/bad.uid/role").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"labo\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void creation_201_sans_secret_en_base() throws Exception {
    mvc.perform(post("/api/admin/users").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"n@leoni.com\",\"password\":\"motdepasse1\",\"displayName\":\"Nouveau\","
                + "\"matricule\":\"123\",\"role\":\"crimp\",\"pin\":\"1234\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uid").isNotEmpty());
    assertThat(directory.profiles.values()).allSatisfy(p -> assertThat(p).doesNotContainKeys("password", "pin"));
  }

  @Test
  void requete_invalide_400_sans_detail_interne() throws Exception {
    mvc.perform(post("/api/admin/users").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"pas-un-email\",\"password\":\"court\",\"displayName\":\"\",\"matricule\":\"1\",\"role\":\"crimp\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.trace").doesNotExist())
        .andExpect(jsonPath("$.exception").doesNotExist());
  }

  @Test
  void reinitialisation_mot_de_passe_revoque_les_sessions() throws Exception {
    mvc.perform(put("/api/admin/users/labo1/password").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"nouveaumdp1\"}"))
        .andExpect(status().isNoContent());
    assertThat(directory.revoked).contains("labo1");
  }

  // ── Surface exposée ──────────────────────────────────────────────────────────
  @Test
  void seule_la_sante_est_publique() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mvc.perform(get("/autre")).andExpect(status().isUnauthorized());
  }

  @Test
  void cors_limite_aux_origines_configurees() throws Exception {
    mvc.perform(options("/api/me").header("Origin", "https://crimping.example")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://crimping.example"));
    mvc.perform(options("/api/me").header("Origin", "https://pirate.example")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }
}
