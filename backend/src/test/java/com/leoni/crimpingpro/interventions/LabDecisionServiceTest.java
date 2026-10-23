package com.leoni.crimpingpro.interventions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.interventions.LabDecisionService.LabForm;
import com.leoni.crimpingpro.interventions.LabDecisionService.Outcome;
import com.leoni.crimpingpro.internal.DataSpace;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.security.Role;
import com.leoni.crimpingpro.support.InMemoryStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LabDecisionServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneOffset.UTC);
  private final long now = clock.millis();

  private final AppPrincipal labo = new AppPrincipal("labo1", "l@x", Role.LABO, true, "Laborantin", "L1");
  private final AppPrincipal labo2 = new AppPrincipal("labo2", "l2@x", Role.LABO, true, "Autre labo", "L2");
  private final AppPrincipal crimp = new AppPrincipal("crimp1", "c@x", Role.CRIMP, true, "Tech", "M1");
  private final AppPrincipal viewer = new AppPrincipal("v1", "v@x", Role.VIEWER, true, "Lecteur", null);

  private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  private InMemoryStore store;
  private LabDecisionService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryStore();
    service = new LabDecisionService(store, new DataSpace(store), new PermissionService(), clock);
  }

  // ── Jeu d'essai : un bon tel que l'application l'écrit ─────────────────────
  private static Map<String, Object> bon(String status, String type) {
    Map<String, Object> pieces = new LinkedHashMap<>();
    pieces.put("pAv", true);
    Map<String, Object> crimping = new LinkedHashMap<>();
    crimping.put("type", type);
    crimping.put("subType", "");
    crimping.put("piecesChanged", pieces);
    crimping.put("filledBy", Map.of("uid", "crimp1", "name", "Tech", "matricule", "M1"));
    crimping.put("photos", List.of("data:image/jpeg;base64,/9j/AA=="));
    crimping.put("cycles", 120000L);
    List<Object> history = new ArrayList<>();
    history.add(Map.of("status", "draft", "at", 1000L, "by", "crimp1", "byName", "Tech"));
    history.add(Map.of("status", "submitted", "at", 2000L, "by", "crimp1", "byName", "Tech"));
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("numBon", 42L);
    b.put("status", status);
    b.put("statusHistory", history);
    b.put("tool", Map.of("toolId", "t-1", "refOutil", "R1"));
    b.put("crimping", crimping);
    b.put("sla", Map.of("submittedAt", 2000L));
    b.put("_indexes", Map.of("status", status, "yearMonth", "2026-10", "toolId", "t-1"));
    b.put("locked", false);
    b.put("pdrPreparation", Map.of("status", "pending"));
    b.put("createdAt", 1000L);
    b.put("updatedAt", 2000L);
    return b;
  }

  private void seed(Map<String, Object> bon) {
    store.set("interventions/42", bon);
  }

  private Map<String, Object> saved() {
    return store.children("interventions/42");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> history(Map<String, Object> bon) {
    return (List<Map<String, Object>>) bon.get("statusHistory");
  }

  private static LabForm form() {
    return new LabForm("Labo Tech", "L9", "C-1", "0.5", Map.of("chanfrein", true, "flexion", false),
        null, null);
  }

  private static Map<String, Object> coupe() {
    return Map.of("fileName", "coupe.png", "fileSize", 70L, "mimeType", "image/png", "dataUrl", PNG,
        "uploadedAt", 5L);
  }

  private static HttpStatus statusOf(Throwable t) {
    return ((ApiException) t).status();
  }

  // ── Validation ─────────────────────────────────────────────────────────────
  @Test
  void validation_ecrit_les_memes_champs_que_l_application() {
    seed(bon("submitted", "preventive"));

    Outcome o = service.validate(labo, "42", "req-00000001", "production", form());

    assertThat(o.applied()).isTrue();
    assertThat(o.replay()).isFalse();
    assertThat(o.decidedAt()).isEqualTo(now);
    Map<String, Object> b = saved();
    assertThat(b).containsEntry("status", "validated").containsEntry("locked", true)
        .containsEntry("updatedAt", now);
    Map<String, Object> lab = map(b.get("lab"));
    assertThat(lab).containsEntry("decision", "validated").containsEntry("filledAt", now)
        .containsEntry("technicienNom", "Labo Tech").containsEntry("technicienMatricule", "L9")
        .containsEntry("filledBy", Map.of("uid", "labo1", "name", "Laborantin", "matricule", "L1"))
        .containsEntry("connexion", Map.of("refConnexion", "C-1", "sectionCable", "0.5"))
        .containsEntry("conformites", Map.of("chanfrein", true, "flexion", false));
    assertThat(map(b.get("sla"))).containsEntry("submittedAt", 2000L).containsEntry("decidedAt", now)
        .containsEntry("durationMs", now - 2000L);
    assertThat(map(b.get("_indexes"))).containsEntry("status", "validated")
        .containsEntry("yearMonth", "2026-10");
    assertThat(history(b)).hasSize(3).last().satisfies(e -> assertThat(e)
        .containsEntry("status", "validated").containsEntry("at", now).containsEntry("by", "labo1")
        .containsEntry("byName", "Laborantin").containsEntry("requestId", "req-00000001"));
    // Le reste du bon est intact
    assertThat(map(b.get("crimping"))).containsKey("photos").containsEntry("cycles", 120000L);
    assertThat(b).containsKey("pdrPreparation").containsEntry("numBon", 42L);

    assertThat(store.children("notifications/crimp1").values()).singleElement().satisfies(n ->
        assertThat(map(n)).containsEntry("type", "intervention_validated")
            .containsEntry("message", "Ton bon N°42 a été validé ✅")
            .containsEntry("interventionId", 42L).containsEntry("read", false)
            .containsEntry("createdAt", now));
    assertThat(store.children("auditLog").values()).singleElement().satisfies(a ->
        assertThat(map(a)).containsEntry("action", "intervention.validate")
            .containsEntry("entity", "interventions/42").containsEntry("uid", "labo1")
            .containsEntry("source", "api").containsEntry("requestId", "req-00000001"));
  }

  @Test
  void homologation_reste_deverrouillee_jusqu_a_la_livraison_du_dossier() {
    seed(bon("submitted", "homologation"));
    service.validate(labo, "42", "req-00000001", null, form());
    assertThat(saved()).containsEntry("status", "validated").containsEntry("locked", false);
  }

  @Test
  void la_meme_requete_rejouee_ne_fait_aucune_double_operation() {
    seed(bon("submitted", "preventive"));
    service.validate(labo, "42", "req-00000001", null, form());

    Outcome again = service.validate(labo, "42", "req-00000001", null, form());

    assertThat(again.applied()).isFalse();
    assertThat(again.replay()).isTrue();
    assertThat(again.decidedAt()).isEqualTo(now);
    assertThat(history(saved())).hasSize(3);
    assertThat(store.children("notifications/crimp1")).hasSize(1);
    assertThat(store.children("auditLog")).hasSize(1);
  }

  @Test
  void une_seconde_decision_sur_un_bon_deja_decide_est_refusee() {
    seed(bon("submitted", "preventive"));
    service.validate(labo, "42", "req-00000001", null, form());

    assertThatThrownBy(() -> service.reject(labo2, "42", "req-00000002", null, "Non conforme"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT))
        .hasMessageContaining("déjà été validé");
    assertThatThrownBy(() -> service.validate(labo, "42", "req-00000003", null, form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
    assertThat(history(saved())).hasSize(3);
  }

  @Test
  void un_identifiant_de_requete_repris_par_un_autre_compte_est_refuse() {
    seed(bon("submitted", "preventive"));
    service.validate(labo, "42", "req-00000001", null, form());
    assertThatThrownBy(() -> service.validate(labo2, "42", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void coupe_obligatoire_si_changement_conducteur() {
    Map<String, Object> b = bon("submitted", "preventive");
    map(map(b.get("crimping")).get("piecesChanged")).put("pAr", true);
    seed(b);

    assertThatThrownBy(() -> service.validate(labo, "42", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t).value()).isEqualTo(422))
        .hasMessageContaining("Coupe métallographique OBLIGATOIRE");
    assertThat(saved()).containsEntry("status", "submitted");

    LabForm withCoupe = new LabForm("Labo Tech", "L9", "C-1", "0.5", Map.of(), coupe(), PNG);
    service.validate(labo, "42", "req-00000002", null, withCoupe);
    Map<String, Object> lab = map(saved().get("lab"));
    assertThat(map(lab.get("coupeMetallo"))).containsEntry("dataUrl", PNG);
    assertThat(lab).containsEntry("signatureDataUrl", PNG);
  }

  @Test
  void coupe_obligatoire_aussi_pour_le_sous_type_coupe() {
    Map<String, Object> b = bon("submitted", "preventive");
    map(b.get("crimping")).put("subType", "coupe");
    seed(b);
    assertThatThrownBy(() -> service.validate(labo, "42", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t).value()).isEqualTo(422));
  }

  @Test
  void une_coupe_deja_jointe_est_conservee() {
    Map<String, Object> b = bon("submitted", "preventive");
    map(map(b.get("crimping")).get("piecesChanged")).put("eAr", true);
    b.put("lab", Map.of("coupeMetallo", coupe()));
    seed(b);

    service.validate(labo, "42", "req-00000001", null, form());

    assertThat(map(map(saved().get("lab")).get("coupeMetallo"))).containsEntry("fileName", "coupe.png");
  }

  @Test
  void historique_stocke_en_objet_avec_des_trous() {
    Map<String, Object> b = bon("submitted", "preventive");
    Map<String, Object> sparse = new LinkedHashMap<>();
    sparse.put("0", Map.of("status", "draft", "at", 1L));
    sparse.put("2", Map.of("status", "submitted", "at", 2L));
    b.put("statusHistory", sparse);
    seed(b);

    service.validate(labo, "42", "req-00000001", null, form());

    assertThat(map(saved().get("statusHistory"))).containsKeys("0", "2", "3");
  }

  // ── Refus ──────────────────────────────────────────────────────────────────
  @Test
  void refus_ecrit_les_memes_champs_que_l_application() {
    seed(bon("submitted", "preventive"));

    service.reject(labo, "42", "req-00000001", null, "  Sertissage non conforme ");

    Map<String, Object> b = saved();
    assertThat(b).containsEntry("status", "rejected").containsEntry("locked", false)
        .containsEntry("updatedAt", now);
    assertThat(map(b.get("lab"))).containsEntry("decision", "rejected")
        .containsEntry("rejectReason", "Sertissage non conforme")
        .containsEntry("filledBy", Map.of("uid", "labo1", "name", "Laborantin"));
    assertThat(map(b.get("sla"))).containsEntry("decidedAt", now).containsEntry("durationMs", now - 2000L);
    assertThat(map(b.get("_indexes"))).containsEntry("status", "rejected");
    assertThat(history(b)).last().satisfies(e -> assertThat(e).containsEntry("status", "rejected")
        .containsEntry("reason", "Sertissage non conforme"));
    assertThat(store.children("notifications/crimp1").values()).singleElement().satisfies(n ->
        assertThat(map(n)).containsEntry("message", "Ton bon N°42 a été refusé : Sertissage non conforme"));
  }

  @Test
  void refus_sans_motif_ou_d_un_dossier_livre_refuse() {
    seed(bon("delivered", "homologation"));
    assertThatThrownBy(() -> service.reject(labo, "42", "req-00000001", null, " "))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> service.reject(labo, "42", "req-00000001", null, "Motif"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
  }

  // ── Livraison du dossier ───────────────────────────────────────────────────
  @Test
  void livraison_du_dossier_apres_validation_d_une_homologation() {
    Map<String, Object> b = bon("validated", "homologation");
    b.put("lab", Map.of("decision", "validated", "coupeMetallo", coupe()));
    seed(b);
    Map<String, Object> report = Map.of("fileName", "rapport.pdf", "dataUrl", "data:application/pdf;base64,JVBERi0=");

    service.deliver(labo, "42", "req-00000001", null, "Second Tech", "M2", report);

    Map<String, Object> saved = saved();
    assertThat(saved).containsEntry("status", "delivered").containsEntry("locked", true);
    Map<String, Object> lab = map(saved.get("lab"));
    assertThat(lab).containsEntry("deliveredAt", now).containsEntry("reportInjected", true)
        .containsEntry("tech2Name", "Second Tech").containsEntry("tech2Matricule", "M2")
        .containsEntry("deliveredBy", Map.of("uid", "labo1", "name", "Laborantin", "matricule", "L1"))
        .containsEntry("reportData", report)
        .containsEntry("decision", "validated").containsKey("coupeMetallo");
    assertThat(map(saved.get("_indexes"))).containsEntry("status", "delivered");
    assertThat(history(saved)).last().satisfies(e -> assertThat(e).containsEntry("status", "delivered")
        .containsEntry("type", "homologation"));
    assertThat(store.children("notifications/crimp1").values()).singleElement().satisfies(n ->
        assertThat(map(n)).containsEntry("type", "intervention_delivered")
            .containsEntry("message", "Ton bon N°42 : dossier livré 📁"));
    assertThat(store.children("auditLog").values()).singleElement().satisfies(a ->
        assertThat(map(a)).containsEntry("action", "intervention.deliverDossier")
            .containsEntry("type", "homologation").containsEntry("tech2Name", "Second Tech"));
  }

  @Test
  void livraison_sans_saisie_reprend_le_profil_et_garde_le_rapport_existant() {
    Map<String, Object> b = bon("validated", "requalification");
    b.put("lab", Map.of("reportData", Map.of("fileName", "ancien.pdf")));
    seed(b);

    service.deliver(labo, "42", "req-00000001", null, "", null, null);

    assertThat(map(saved().get("lab"))).containsEntry("tech2Name", "Laborantin")
        .containsEntry("tech2Matricule", "L1")
        .containsEntry("reportData", Map.of("fileName", "ancien.pdf"));
  }

  @Test
  void livraison_refusee_avant_validation_ou_pour_une_preventive() {
    seed(bon("submitted", "homologation"));
    assertThatThrownBy(() -> service.deliver(labo, "42", "req-00000001", null, null, null, null))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT))
        .hasMessageContaining("doit être validé");
    seed(bon("validated", "preventive"));
    assertThatThrownBy(() -> service.deliver(labo, "42", "req-00000002", null, null, null, null))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT))
        .hasMessageContaining("réservée à Homologation");
  }

  // ── Validation automatique (réglage mécanique seul) ────────────────────────
  @Test
  void validation_automatique_ecrit_les_champs_et_debloque_l_outil() {
    Map<String, Object> b = bon("draft", "preventive");
    map(b.get("crimping")).put("reglageMecanique", true);
    seed(b);
    store.set("tools/t-1", Map.of("serial", "S1", "requalifTimeStatus", "blocked", "maintenanceStatus", "due"));

    Outcome o = service.autoValidate(crimp, "42", "req-00000001", null);

    assertThat(o.applied()).isTrue();
    Map<String, Object> saved = saved();
    assertThat(saved).containsEntry("status", "validated").containsEntry("autoValidated", true)
        .containsEntry("locked", true);
    assertThat(map(saved.get("lab"))).containsEntry("autoValidated", true)
        .containsEntry("decision", "validated").containsEntry("note", LabDecisionService.AUTO_NOTE)
        .containsEntry("filledBy", Map.of("uid", "crimp1", "name", "Tech", "matricule", "M1", "auto", true));
    assertThat(map(saved.get("sla"))).containsEntry("submittedAt", now).containsEntry("decidedAt", now)
        .containsEntry("durationMs", 0L);
    List<Map<String, Object>> h = history(saved);
    assertThat(h).hasSize(4);
    assertThat(h.get(2)).containsEntry("status", "submitted").containsEntry("auto", true)
        .doesNotContainKey("requestId");
    assertThat(h.get(3)).containsEntry("status", "validated").containsEntry("auto", true)
        .containsEntry("requestId", "req-00000001");
    assertThat(store.children("tools/t-1")).containsEntry("requalifTimeStatus", "ok")
        .containsEntry("requalifStrokesStatus", "ok").containsEntry("daysInUseStatus", "ok")
        .containsEntry("maintenanceStatus", "ok").containsEntry("lastRequalifAt", now)
        .containsEntry("lastRequalifBonReg", 42L).containsEntry("serial", "S1");
    assertThat(store.children("auditLog").values()).extracting(a -> map(a).get("action"))
        .containsExactlyInAnyOrder("intervention.autovalidate", "tool.unblock.auto");

    // Rejouée : ni seconde écriture, ni second déblocage
    Outcome again = service.autoValidate(crimp, "42", "req-00000001", null);
    assertThat(again.replay()).isTrue();
    assertThat(store.children("auditLog")).hasSize(2);
  }

  @Test
  void validation_automatique_refusee_hors_reglage_mecanique_seul() {
    seed(bon("draft", "preventive"));
    assertThatThrownBy(() -> service.autoValidate(crimp, "42", "req-00000001", null))
        .satisfies(t -> assertThat(statusOf(t).value()).isEqualTo(422));

    Map<String, Object> b = bon("draft", "preventive");
    map(b.get("crimping")).put("reglageMecanique", true);
    map(b.get("crimping")).put("subType", "binding");
    seed(b);
    assertThatThrownBy(() -> service.autoValidate(crimp, "42", "req-00000002", null))
        .satisfies(t -> assertThat(statusOf(t).value()).isEqualTo(422));
  }

  @Test
  void un_outil_absent_du_catalogue_n_est_pas_cree() {
    Map<String, Object> b = bon("draft", "preventive");
    map(b.get("crimping")).put("reglageMecanique", true);
    seed(b);
    service.autoValidate(crimp, "42", "req-00000001", null);
    assertThat(store.read("tools/t-1")).isNull();
  }

  // ── Espace TEST / PROD, droits, cas limites ────────────────────────────────
  @Test
  void en_mode_test_tout_est_ecrit_sous_test_data() {
    store.set("system/mode", "test");
    store.set("test_data/interventions/42", bon("submitted", "preventive"));

    service.validate(labo, "42", "req-00000001", "test", form());

    assertThat(store.children("test_data/interventions/42")).containsEntry("status", "validated");
    assertThat(store.children("test_data/notifications/crimp1")).hasSize(1);
    assertThat(store.children("test_data/auditLog")).hasSize(1);
    assertThat(store.read("interventions/42")).isNull();
    assertThat(store.read("notifications")).isNull();
  }

  @Test
  void mode_affiche_different_du_serveur_refuse() {
    store.set("system/mode", "test");
    store.set("test_data/interventions/42", bon("submitted", "preventive"));
    assertThatThrownBy(() -> service.validate(labo, "42", "req-00000001", "production", form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.CONFLICT));
    assertThat(store.children("test_data/interventions/42")).containsEntry("status", "submitted");
  }

  @Test
  void bon_introuvable_ou_identifiants_invalides() {
    assertThatThrownBy(() -> service.validate(labo, "42", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.NOT_FOUND));
    assertThatThrownBy(() -> service.validate(labo, "4/2", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> service.validate(labo, "42", "court", null, form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void droits_identiques_a_l_application() {
    seed(bon("submitted", "preventive"));
    assertThatThrownBy(() -> service.validate(crimp, "42", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.reject(viewer, "42", "req-00000001", null, "x"))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.autoValidate(labo, "42", "req-00000001", null))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    AppPrincipal desactive = new AppPrincipal("labo3", "x@x", Role.LABO, false, "Ancien", null);
    assertThatThrownBy(() -> service.validate(desactive, "42", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
    assertThat(saved()).containsEntry("status", "submitted");
  }

  @Test
  void bon_qui_depasserait_la_taille_maximale_refuse() {
    Map<String, Object> b = bon("submitted", "preventive");
    map(b.get("crimping")).put("photos", List.of("x".repeat((int) LabDecisionService.MAX_NODE_CHARS)));
    seed(b);
    assertThatThrownBy(() -> service.validate(labo, "42", "req-00000001", null, form()))
        .satisfies(t -> assertThat(statusOf(t).value()).isEqualTo(413));
    assertThat(saved()).containsEntry("status", "submitted");
  }

  @Test
  void decisions_simultanees_une_seule_est_appliquee() throws Exception {
    seed(bon("submitted", "preventive"));
    CountDownLatch start = new CountDownLatch(1);
    Callable<String> validate = () -> {
      start.await();
      return service.validate(labo, "42", "req-0000000A", null, form()).status();
    };
    Callable<String> reject = () -> {
      start.await();
      return service.reject(labo2, "42", "req-0000000B", null, "Non conforme").status();
    };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<String> a = pool.submit(validate);
      Future<String> r = pool.submit(reject);
      start.countDown();
      int ok = 0;
      int conflicts = 0;
      for (Future<String> f : List.of(a, r)) {
        try {
          f.get();
          ok++;
        } catch (java.util.concurrent.ExecutionException e) {
          assertThat(statusOf(e.getCause())).isEqualTo(HttpStatus.CONFLICT);
          conflicts++;
        }
      }
      assertThat(ok).isEqualTo(1);
      assertThat(conflicts).isEqualTo(1);
      assertThat(history(saved())).hasSize(3);
      assertThat(store.children("notifications/crimp1")).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
