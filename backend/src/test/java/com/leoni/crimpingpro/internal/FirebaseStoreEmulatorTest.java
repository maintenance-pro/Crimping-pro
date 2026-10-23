package com.leoni.crimpingpro.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;
import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.interventions.LabDecisionService;
import com.leoni.crimpingpro.interventions.LabDecisionService.LabForm;
import com.leoni.crimpingpro.internal.Store.Tx;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.security.Role;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpStatus;

/**
 * FirebaseStore et LabDecisionService sur l'émulateur Realtime Database local (projet demo-,
 * jamais la production). Vérifie le vrai comportement des transactions Firebase : premier passage
 * sans donnée, rejeu par le serveur, écritures concurrentes.
 *
 * <p>Lancé seulement si l'émulateur est installé (CRIMPING_TOOLS, par défaut
 * ~/dev-tools/crimping-pro/firebase-database-emulator-4.11.2.jar).
 */
@EnabledIf("emulatorInstalled")
class FirebaseStoreEmulatorTest {

  private static Process emulator;
  private static FirebaseApp app;
  private static FirebaseStore store;

  static Path emulatorJar() {
    String tools = System.getenv().getOrDefault("CRIMPING_TOOLS",
        System.getProperty("user.home") + File.separator + "dev-tools" + File.separator + "crimping-pro");
    return Path.of(tools, "firebase-database-emulator-4.11.2.jar");
  }

  static boolean emulatorInstalled() {
    return emulatorJar().toFile().isFile();
  }

  @BeforeAll
  static void startEmulator() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    emulator = new ProcessBuilder(javaBin, "-Duser.language=en", "-jar", emulatorJar().toString(),
        "--host", "127.0.0.1", "--port", String.valueOf(port))
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start();
    long deadline = System.currentTimeMillis() + 60_000;
    while (true) {
      try (Socket ignored = new Socket("127.0.0.1", port)) {
        break;
      } catch (IOException e) {
        if (System.currentTimeMillis() > deadline || !emulator.isAlive()) {
          throw new IllegalStateException("Émulateur non démarré", e);
        }
        Thread.sleep(250);
      }
    }
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.create(new AccessToken("owner",
            Date.from(Instant.now().plus(Duration.ofDays(1))))))
        .setProjectId("demo-crimping")
        .setDatabaseUrl("http://127.0.0.1:" + port + "?ns=demo-crimping-it")
        .build();
    app = FirebaseApp.initializeApp(options, "emulateur-" + UUID.randomUUID());
    store = new FirebaseStore(FirebaseDatabase.getInstance(app));
  }

  @AfterAll
  static void stopEmulator() {
    if (app != null) {
      app.delete();
    }
    if (emulator != null) {
      emulator.destroy();
    }
  }

  @BeforeEach
  void clear() {
    Map<String, Object> wipe = new HashMap<>();
    for (String key : List.of("it", "interventions", "notifications", "auditLog", "tools", "system", "test_data")) {
      wipe.put(key, null);
    }
    store.update("/", wipe);
  }

  @Test
  void noeud_absent_rien_n_est_ecrit() {
    Tx<String> tx = store.transact("it/absent", current ->
        current == null ? Tx.nothingToWrite("introuvable") : Tx.abort("trouve"));
    assertThat(tx.result()).isEqualTo("introuvable");
    assertThat(store.read("it/absent")).isNull();
  }

  @Test
  void noeud_existant_la_fonction_recoit_la_vraie_valeur() {
    store.update("it", Map.of("bon", Map.of("status", "submitted", "n", 1L)));
    Tx<String> tx = store.transact("it/bon", current -> {
      if (current == null) {
        return Tx.nothingToWrite("introuvable");
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> v = new HashMap<>((Map<String, Object>) current);
      v.put("status", "validated");
      return Tx.commit(v, (String) ((Map<?, ?>) current).get("status"));
    });
    assertThat(tx.result()).isEqualTo("submitted");
    assertThat(store.read("it/bon")).isEqualTo(Map.of("status", "validated", "n", 1L));
  }

  @Test
  void creation_unique_meme_si_rejouee() {
    Tx<Boolean> first = store.transact("it/unique", c -> c == null ? Tx.commit(Map.of("v", 1L), true) : Tx.abort(false));
    Tx<Boolean> second = store.transact("it/unique", c -> c == null ? Tx.commit(Map.of("v", 2L), true) : Tx.abort(false));
    assertThat(first.result()).isTrue();
    assertThat(second.result()).isFalse();
    assertThat(store.read("it/unique")).isEqualTo(Map.of("v", 1L));
  }

  @Test
  void ecritures_concurrentes_aucune_perdue() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Future<?>> all = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        all.add(pool.submit(() -> store.transact("it/compteur", c ->
            Tx.commit((c == null ? 0L : ((Number) c).longValue()) + 1, null))));
      }
      for (Future<?> f : all) {
        f.get();
      }
    } finally {
      pool.shutdownNow();
    }
    assertThat(store.read("it/compteur")).isEqualTo(20L);
  }

  @Test
  void decision_labo_de_bout_en_bout_sur_la_base() {
    Map<String, Object> bon = new HashMap<>();
    bon.put("numBon", 42L);
    bon.put("status", "submitted");
    bon.put("statusHistory", List.of(Map.of("status", "submitted", "at", 2000L, "by", "crimp1")));
    bon.put("crimping", Map.of("type", "preventive", "filledBy", Map.of("uid", "crimp1"),
        "photos", List.of("data:image/jpeg;base64,/9j/AA==")));
    bon.put("sla", Map.of("submittedAt", 2000L));
    store.update("interventions", Map.of("42", bon));

    LabDecisionService service = new LabDecisionService(store, new DataSpace(store),
        new PermissionService(), Clock.systemUTC());
    AppPrincipal labo = new AppPrincipal("labo1", "l@x", Role.LABO, true, "Laborantin", "L1");
    LabForm form = new LabForm("Labo", "L1", "C-1", "0.5", Map.of("flexion", true), null, null);

    var first = service.validate(labo, "42", "req-00000001", "production", form);
    var again = service.validate(labo, "42", "req-00000001", "production", form);

    assertThat(first.applied()).isTrue();
    assertThat(again.replay()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> saved = (Map<String, Object>) store.read("interventions/42");
    assertThat(saved).containsEntry("status", "validated").containsEntry("locked", true);
    assertThat((List<?>) saved.get("statusHistory")).hasSize(2);
    assertThat((Map<?, ?>) store.read("notifications/crimp1")).hasSize(1);
    assertThatThrownBy(() -> service.reject(labo, "42", "req-00000002", null, "Motif"))
        .satisfies(t -> assertThat(((ApiException) t).status()).isEqualTo(HttpStatus.CONFLICT));
    assertThatThrownBy(() -> service.validate(labo, "999", "req-00000003", null, form))
        .satisfies(t -> assertThat(((ApiException) t).status()).isEqualTo(HttpStatus.NOT_FOUND));
    assertThat(store.read("interventions/999")).isNull();
  }
}
