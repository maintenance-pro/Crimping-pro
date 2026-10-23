package com.leoni.crimpingpro.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leoni.crimpingpro.security.PrincipalResolver;
import com.leoni.crimpingpro.support.FakeTokenVerifier;
import com.leoni.crimpingpro.support.InMemoryStore;
import com.leoni.crimpingpro.support.InMemoryUserDirectory;
import com.leoni.crimpingpro.support.TestSupport;
import java.util.List;
import java.util.Map;
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
    "app.limits.decision-body-bytes=200000",
    "app.rate-limit.decisions-per-minute=100"
})
@Import(TestSupport.class)
class InterventionApiTest {

  private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  @Autowired WebApplicationContext context;
  @Autowired FakeTokenVerifier tokens;
  @Autowired InMemoryUserDirectory directory;
  @Autowired InMemoryStore store;
  @Autowired PrincipalResolver resolver;

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    // Comme dans le serveur réel : le contrôle de taille passe avant la sécurité
    mvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(context.getBean(RequestSizeLimitFilter.class))
        .apply(springSecurity()).build();
    directory.reset();
    store.reset();
    resolver.invalidateAll();
    directory.seed("labo1", "labo", true);
    directory.seed("crimp1", "crimp", true);
    store.set("interventions/42", Map.of(
        "numBon", 42L,
        "status", "submitted",
        "statusHistory", List.of(Map.of("status", "submitted", "at", 2000L, "by", "crimp1")),
        "crimping", Map.of("type", "preventive", "piecesChanged", Map.of("pAr", true),
            "filledBy", Map.of("uid", "crimp1", "name", "Tech")),
        "sla", Map.of("submittedAt", 2000L)));
  }

  private String bearer(String uid) {
    return "Bearer " + tokens.tokenFor(uid);
  }

  private static String validateBody(String requestId, String coupeDataUrl) {
    return """
        {"requestId":"%s","mode":"production","technicienNom":"Labo Tech",
         "connexion":{"refConnexion":"C-1","sectionCable":"0.5"},"conformites":{"chanfrein":true},
         "coupeMetallo":{"fileName":"coupe.png","fileSize":70,"mimeType":"image/png","dataUrl":"%s"},
         "status":"validated","by":"sa","lab":{"decision":"rejected"}}
        """.formatted(requestId, coupeDataUrl);
  }

  @Test
  void validation_par_le_labo_auteur_venant_du_jeton() throws Exception {
    mvc.perform(post("/api/interventions/42/validate").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content(validateBody("req-00000001", PNG)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(true))
        .andExpect(jsonPath("$.replay").value(false))
        .andExpect(jsonPath("$.status").value("validated"));
    @SuppressWarnings("unchecked")
    Map<String, Object> lab = (Map<String, Object>) store.children("interventions/42").get("lab");
    assertThat(lab).containsEntry("decision", "validated")
        .containsEntry("filledBy", Map.of("uid", "labo1", "name", "Nom labo1", "matricule", ""));
  }

  @Test
  void requete_renvoyee_reponse_identique_sans_double_ecriture() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/api/interventions/42/validate").header("Authorization", bearer("labo1"))
          .contentType(MediaType.APPLICATION_JSON).content(validateBody("req-00000001", PNG)))
          .andExpect(status().isOk());
    }
    mvc.perform(post("/api/interventions/42/validate").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content(validateBody("req-00000001", PNG)))
        .andExpect(jsonPath("$.applied").value(false))
        .andExpect(jsonPath("$.replay").value(true));
    assertThat(store.children("notifications/crimp1")).hasSize(1);
  }

  @Test
  void sans_jeton_401_et_technicien_403() throws Exception {
    mvc.perform(post("/api/interventions/42/validate")
            .contentType(MediaType.APPLICATION_JSON).content(validateBody("req-00000001", PNG)))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/interventions/42/validate").header("Authorization", bearer("crimp1"))
            .contentType(MediaType.APPLICATION_JSON).content(validateBody("req-00000001", PNG)))
        .andExpect(status().isForbidden());
    assertThat(store.children("interventions/42")).containsEntry("status", "submitted");
  }

  @Test
  void fichier_html_deguise_refuse_et_bon_inchange() throws Exception {
    String disguised = "data:image/png;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==";
    mvc.perform(post("/api/interventions/42/validate").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content(validateBody("req-00000001", disguised)))
        .andExpect(status().isBadRequest());
    assertThat(store.children("interventions/42")).containsEntry("status", "submitted");
  }

  @Test
  void coupe_manquante_422_avec_message_clair() throws Exception {
    mvc.perform(post("/api/interventions/42/validate").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"req-00000001\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Coupe métallographique")));
  }

  @Test
  void numero_de_bon_ou_identifiant_de_requete_invalides_400() throws Exception {
    mvc.perform(post("/api/interventions/4.2/validate").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content(validateBody("req-00000001", PNG)))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/interventions/42/reject").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"x\",\"reason\":\"r\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void corps_trop_volumineux_413_avant_toute_lecture() throws Exception {
    String huge = "{\"requestId\":\"req-00000001\",\"reason\":\"" + "x".repeat(250_000) + "\"}";
    mvc.perform(post("/api/interventions/42/reject").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content(huge))
        .andExpect(status().is(413));
    String bigAdmin = "{\"role\":\"" + "x".repeat(70_000) + "\"}";
    mvc.perform(post("/api/admin/users").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content(bigAdmin))
        .andExpect(status().is(413));
    assertThat(store.children("interventions/42")).containsEntry("status", "submitted");
  }

  @Test
  void validation_automatique_par_le_technicien() throws Exception {
    store.set("interventions/43", Map.of("status", "draft",
        "crimping", Map.of("type", "preventive", "reglageMecanique", true,
            "filledBy", Map.of("uid", "crimp1"))));
    mvc.perform(post("/api/interventions/43/auto-validate").header("Authorization", bearer("crimp1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"req-00000009\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(true));
    mvc.perform(post("/api/interventions/42/auto-validate").header("Authorization", bearer("labo1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"req-00000010\"}"))
        .andExpect(status().isForbidden());
  }
}
