package com.leoni.crimpingpro.pin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.config.AppProperties;
import com.leoni.crimpingpro.pin.PinService.EnrolledDevice;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.security.RateLimiter;
import com.leoni.crimpingpro.security.Role;
import com.leoni.crimpingpro.security.VerifiedToken;
import com.leoni.crimpingpro.support.FakeTokenMinter;
import com.leoni.crimpingpro.support.InMemoryStore;
import com.leoni.crimpingpro.support.InMemoryUserDirectory;
import com.leoni.crimpingpro.support.MutableClock;
import com.leoni.crimpingpro.support.RecordingAuditService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PinServiceTest {

  private static final String PEPPER = "poivre-de-test-0123456789abcdef-0123";
  private static final String PIN = "583092";

  private final AppPrincipal admin = new AppPrincipal("admin1", "a@x", Role.ADMIN, true, "Admin", null);
  private final AppPrincipal crimp = new AppPrincipal("crimp1", "c@x", Role.CRIMP, true, "Tech", "M1");

  private MutableClock clock;
  private InMemoryStore store;
  private InMemoryUserDirectory directory;
  private RecordingAuditService audit;
  private PinSessionGuard guard;
  private PinService service;
  private EnrolledDevice poste;

  private static AppProperties props(String pepper) {
    return new AppProperties(null, null, new AppProperties.RateLimit(30, 60), null,
        new AppProperties.Pin(pepper, 4, 12));
  }

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-10-22T08:00:00Z"));
    store = new InMemoryStore();
    directory = new InMemoryUserDirectory();
    audit = new RecordingAuditService();
    directory.seed("admin1", "admin", true);
    directory.seed("op1", "crimp", true);
    directory.profiles.get("op1").put("matricule", "M7");
    directory.seed("op2", "crimp", true);
    directory.profiles.get("op2").put("matricule", "M8");
    guard = new PinSessionGuard(store, clock, props(PEPPER));
    service = newService(PEPPER);
    poste = service.enrollDevice(admin, "Poste atelier 1");
    service.setPin(admin, "op1", PIN);
  }

  private PinService newService(String pepper) {
    return new PinService(store, directory, new PermissionService(), audit, new FakeTokenMinter(), guard,
        new RateLimiter(clock), clock, props(pepper));
  }

  private String login(String matricule, String pin) {
    return service.login("10.0.0.1", poste.deviceId(), poste.deviceSecret(), matricule, pin);
  }

  private static int status(Throwable t) {
    return ((ApiException) t).status().value();
  }

  private void failOnce() {
    assertThatThrownBy(() -> login("M7", "000111"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(401))
        .hasMessage(PinService.BAD_CREDENTIALS);
  }

  // ── Enrôlement et stockage ─────────────────────────────────────────────────
  @Test
  void aucun_secret_en_clair_dans_la_base_ni_dans_l_audit() {
    Map<String, Object> device = store.children("secure/devices/" + poste.deviceId());
    assertThat(device).containsEntry("active", true).containsEntry("label", "Poste atelier 1")
        .containsEntry("secretHash", PinService.sha256(poste.deviceSecret()));
    assertThat(device.values()).doesNotContain(poste.deviceSecret());
    Map<String, Object> pin = store.children("secure/pins/op1");
    assertThat((String) pin.get("hash")).startsWith("$2").doesNotContain(PIN);
    assertThat(store.read("secure/pinIndex/M7")).isEqualTo("op1");
    assertThat(audit.entries).allSatisfy(e -> assertThat(e.details().values())
        .doesNotContain(PIN, poste.deviceSecret()));
    assertThat(poste.toString()).doesNotContain(poste.deviceSecret());
  }

  @Test
  void pin_trop_simple_ou_tire_du_matricule_refuse() {
    for (String weak : List.of("123456", "987654", "111111", "121212", "123123", "12345", "abcdef")) {
      assertThatThrownBy(() -> service.setPin(admin, "op2", weak))
          .satisfies(t -> assertThat(status(t)).isEqualTo(400));
    }
    directory.profiles.get("op2").put("matricule", "A4471");
    assertThatThrownBy(() -> service.setPin(admin, "op2", "447190"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
  }

  @Test
  void un_matricule_ne_designe_qu_un_compte_et_l_ancien_index_est_retire() {
    directory.profiles.get("op2").put("matricule", "M7");
    assertThatThrownBy(() -> service.setPin(admin, "op2", "620481"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(409));

    directory.profiles.get("op1").put("matricule", "M70");
    service.setPin(admin, "op1", "620481");
    assertThat(store.read("secure/pinIndex/M7")).isNull();
    assertThat(store.read("secure/pinIndex/M70")).isEqualTo("op1");
  }

  @Test
  void compte_sans_matricule_refuse() {
    directory.profiles.get("op2").remove("matricule");
    assertThatThrownBy(() -> service.setPin(admin, "op2", "620481"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
  }

  @Test
  void seul_un_administrateur_gere_postes_et_pin() {
    assertThatThrownBy(() -> service.enrollDevice(crimp, "x")).satisfies(t -> assertThat(status(t)).isEqualTo(403));
    assertThatThrownBy(() -> service.setPin(crimp, "op1", "620481")).satisfies(t -> assertThat(status(t)).isEqualTo(403));
    assertThatThrownBy(() -> service.revokeDevice(crimp, poste.deviceId())).satisfies(t -> assertThat(status(t)).isEqualTo(403));
    assertThatThrownBy(() -> service.listDevices(crimp)).satisfies(t -> assertThat(status(t)).isEqualTo(403));
  }

  // ── Connexion ──────────────────────────────────────────────────────────────
  @Test
  void connexion_reussie_jeton_limite_au_poste() {
    String token = login("M7", PIN);
    assertThat(token).isEqualTo("custom:op1:{pin_device=" + poste.deviceId() + "}");
    assertThat(store.children("secure/devices/" + poste.deviceId())).containsKey("lastUsedAt");
    assertThat(audit.entries).anySatisfy(e -> assertThat(e.action()).isEqualTo("auth.pinLogin"));
  }

  @Test
  void poste_inconnu_secret_faux_ou_poste_revoque_refuses() {
    assertThatThrownBy(() -> service.login("ip", poste.deviceId(), "faux", "M7", PIN))
        .satisfies(t -> assertThat(status(t)).isEqualTo(401)).hasMessage(PinService.BAD_DEVICE);
    assertThatThrownBy(() -> service.login("ip", "inconnu-0123456789", poste.deviceSecret(), "M7", PIN))
        .satisfies(t -> assertThat(status(t)).isEqualTo(401));
    service.revokeDevice(admin, poste.deviceId());
    assertThatThrownBy(() -> login("M7", PIN)).satisfies(t -> assertThat(status(t)).isEqualTo(401));
  }

  @Test
  void cinq_erreurs_blocage_15_min_puis_le_bon_pin_remet_a_zero() {
    for (int i = 0; i < 5; i++) {
      failOnce();
    }
    assertThatThrownBy(() -> login("M7", PIN)) // même le bon PIN est refusé pendant le blocage
        .satisfies(t -> assertThat(status(t)).isEqualTo(429)).hasMessageContaining("15 min");
    clock.advance(Duration.ofMinutes(15).plusSeconds(1));
    for (int i = 0; i < 4; i++) {
      failOnce();
    }
    assertThat(store.children("secure/pins/op1")).containsEntry("failures", 9L);
    clock.advance(Duration.ofSeconds(1));
    assertThat(login("M7", PIN)).startsWith("custom:op1"); // succès : compteurs remis à zéro
    assertThat(store.children("secure/pins/op1")).containsEntry("failures", 0L).containsEntry("blocked", false);
  }

  @Test
  void dix_erreurs_blocage_jusqu_a_l_intervention_d_un_administrateur() {
    for (int i = 0; i < 5; i++) {
      failOnce();
    }
    clock.advance(Duration.ofMinutes(16));
    for (int i = 0; i < 5; i++) {
      failOnce();
    }
    clock.advance(Duration.ofDays(2));
    assertThatThrownBy(() -> login("M7", PIN))
        .satisfies(t -> assertThat(status(t)).isEqualTo(429)).hasMessageContaining("administrateur");
    service.unlockPin(admin, "op1");
    assertThat(login("M7", PIN)).startsWith("custom:op1");
  }

  @Test
  void vingt_erreurs_sur_un_poste_bloquent_le_poste_une_heure() {
    for (int i = 0; i < 20; i++) {
      final String matricule = "INCONNU" + i;
      assertThatThrownBy(() -> login(matricule, "000111")).satisfies(t -> assertThat(status(t)).isEqualTo(401));
    }
    assertThatThrownBy(() -> login("M7", PIN))
        .satisfies(t -> assertThat(status(t)).isEqualTo(429)).hasMessageContaining("poste");
    clock.advance(Duration.ofHours(1));
    assertThat(login("M7", PIN)).startsWith("custom:op1");
  }

  @Test
  void limite_par_adresse_reseau() {
    for (int i = 0; i < PinService.IP_ATTEMPTS_PER_MINUTE; i++) {
      try {
        service.login("10.9.9.9", poste.deviceId(), "faux", "M7", PIN);
      } catch (ApiException ignored) {
        // 401 attendu
      }
    }
    assertThatThrownBy(() -> service.login("10.9.9.9", poste.deviceId(), poste.deviceSecret(), "M7", PIN))
        .satisfies(t -> assertThat(status(t)).isEqualTo(429));
  }

  @Test
  void compte_desactive_dans_l_application_refuse_meme_avec_le_bon_pin() {
    directory.profiles.get("op1").put("active", false);
    assertThatThrownBy(() -> login("M7", PIN)).satisfies(t -> assertThat(status(t)).isEqualTo(403));
  }

  @Test
  void pin_supprime_connexion_impossible() {
    service.removePin(admin, "op1");
    assertThat(store.read("secure/pins/op1")).isNull();
    assertThat(store.read("secure/pinIndex/M7")).isNull();
    assertThatThrownBy(() -> login("M7", PIN)).satisfies(t -> assertThat(status(t)).isEqualTo(401));
  }

  @Test
  void sans_poivre_configure_la_connexion_par_pin_est_desactivee() {
    PinService off = newService("");
    assertThat(off.enabled()).isFalse();
    assertThatThrownBy(() -> off.login("ip", poste.deviceId(), poste.deviceSecret(), "M7", PIN))
        .satisfies(t -> assertThat(((ApiException) t).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    assertThatThrownBy(() -> off.setPin(admin, "op1", "620481"))
        .satisfies(t -> assertThat(((ApiException) t).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }

  // ── Sessions ouvertes par PIN ──────────────────────────────────────────────
  @Test
  void session_pin_refusee_si_poste_revoque_ou_apres_12_heures() {
    long authTime = clock.millis() / 1000;
    VerifiedToken pinSession = new VerifiedToken("op1", null,
        Map.of("pin_device", poste.deviceId(), "auth_time", authTime));
    VerifiedToken passwordSession = new VerifiedToken("op1", null, Map.of("auth_time", 0L));

    assertThat(guard.allows(pinSession)).isTrue();
    assertThat(guard.allows(passwordSession)).isTrue();
    clock.advance(Duration.ofHours(12).plusMinutes(1));
    assertThat(guard.allows(pinSession)).isFalse();

    VerifiedToken fresh = new VerifiedToken("op1", null,
        Map.of("pin_device", poste.deviceId(), "auth_time", clock.millis() / 1000));
    assertThat(guard.allows(fresh)).isTrue();
    service.revokeDevice(admin, poste.deviceId());
    assertThat(guard.allows(fresh)).isFalse();
  }
}
