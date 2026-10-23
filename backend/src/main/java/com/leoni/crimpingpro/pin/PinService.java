package com.leoni.crimpingpro.pin;

import com.leoni.crimpingpro.audit.AuditService;
import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.config.AppProperties;
import com.leoni.crimpingpro.internal.Json;
import com.leoni.crimpingpro.internal.Store;
import com.leoni.crimpingpro.internal.Store.Tx;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.Permission;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.security.RateLimiter;
import com.leoni.crimpingpro.security.Role;
import com.leoni.crimpingpro.security.Uids;
import com.leoni.crimpingpro.security.UserDirectory;
import com.leoni.crimpingpro.security.UserProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Connexion par PIN, uniquement sur un poste enrôlé par un administrateur.
 *
 * <p>Pourquoi c'est plus qu'un PIN déplacé côté serveur :
 * <ul>
 *   <li>le PIN seul ne suffit pas : il faut aussi le secret du poste, remis une seule fois à
 *       l'enrôlement et conservé uniquement sous forme d'empreinte ; un poste perdu se révoque ;
 *   <li>les essais sont comptés dans la base, donc sur toutes les instances du serveur, et AVANT la
 *       vérification (des essais simultanés ne contournent pas la limite) : 5 erreurs → blocage de
 *       15 min, puis 30, 60… ; 10 erreurs → blocage jusqu'à ce qu'un administrateur intervienne ;
 *       un poste est bloqué une heure après 20 erreurs, tous comptes confondus ; une adresse réseau
 *       est limitée à 30 essais par minute ;
 *   <li>le PIN est conservé sous forme d'empreinte bcrypt avec un poivre secret du serveur ;
 *   <li>la session obtenue dure au plus 12 h et cesse dès que le poste est révoqué (API et règles).
 * </ul>
 *
 * <p>Données (branche secure/, illisible depuis les navigateurs avec les règles de la phase 2) :
 * secure/devices/{id}, secure/deviceGuards/{id}, secure/pins/{uid}, secure/pinIndex/{matricule}.
 */
@Service
public class PinService {

  private static final Logger log = LoggerFactory.getLogger(PinService.class);

  public static final String DEVICE_ID_REGEX = "^[A-Za-z0-9_-]{16,64}$";
  public static final String MATRICULE_REGEX = "^[A-Za-z0-9_-]{1,32}$";
  static final int LOCK_AFTER = 5;
  static final int BLOCK_AFTER = 10;
  static final Duration FIRST_LOCK = Duration.ofMinutes(15);
  static final int DEVICE_FAILURES_PER_HOUR = 20;
  static final int IP_ATTEMPTS_PER_MINUTE = 30;
  private static final long HOUR = Duration.ofHours(1).toMillis();

  static final String BAD_CREDENTIALS = "Matricule ou PIN incorrect.";
  static final String BAD_DEVICE = "Ce poste n'est pas autorisé pour la connexion par PIN. "
      + "Connectez-vous par e-mail ou demandez à un administrateur d'enrôler le poste.";

  public record EnrolledDevice(String deviceId, String deviceSecret) {
    @Override
    public String toString() {
      return "EnrolledDevice[deviceId=" + deviceId + ", deviceSecret=***]";
    }
  }

  public record DeviceView(String deviceId, String label, boolean active, Long createdAt,
      String createdBy, Long revokedAt, Long lastUsedAt) {}

  private enum AccountState { OK, NO_PIN, LOCKED, BLOCKED }

  private record AccountCheck(AccountState state, String hash, long lockedUntil) {}

  private final Store store;
  private final UserDirectory directory;
  private final PermissionService permissions;
  private final AuditService audit;
  private final TokenMinter minter;
  private final PinSessionGuard guard;
  private final RateLimiter rateLimiter;
  private final Clock clock;
  private final PinHasher hasher;
  private final SecureRandom random = new SecureRandom();

  public PinService(Store store, UserDirectory directory, PermissionService permissions,
      AuditService audit, TokenMinter minter, PinSessionGuard guard,
      RateLimiter rateLimiter, Clock clock, AppProperties props) {
    this.store = store;
    this.directory = directory;
    this.permissions = permissions;
    this.audit = audit;
    this.minter = minter;
    this.guard = guard;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
    this.hasher = props.pin().enabled() ? new PinHasher(props.pin().pepper(), props.pin().bcryptCost()) : null;
    if (hasher == null) {
      log.info("Connexion par PIN désactivée (PIN_PEPPER absent ou trop court)");
    }
  }

  public boolean enabled() {
    return hasher != null;
  }

  // ── Connexion ──────────────────────────────────────────────────────────────
  /** Renvoie le jeton personnalisé Firebase qui ouvre la session sur ce poste. */
  public String login(String clientIp, String deviceId, String deviceSecret, String matricule, String pin) {
    requireEnabled();
    if (!rateLimiter.tryAcquire("pin-ip:" + clientIp, IP_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1))) {
      throw ApiException.tooManyRequests("Trop d'essais depuis ce réseau. Réessayez dans une minute.");
    }
    long now = clock.millis();

    // 1. Le poste : enrôlé, actif, secret correct
    if (deviceId == null || !deviceId.matches(DEVICE_ID_REGEX) || deviceSecret == null) {
      throw ApiException.unauthorized(BAD_DEVICE);
    }
    Object device = store.read("secure/devices/" + deviceId);
    if (!(device instanceof Map<?, ?> d) || !Boolean.TRUE.equals(d.get("active"))
        || !sameHash(sha256(deviceSecret), String.valueOf(d.get("secretHash")))) {
      throw ApiException.unauthorized(BAD_DEVICE);
    }
    if (num(store.read("secure/deviceGuards/" + deviceId + "/lockedUntil")) > now) {
      throw ApiException.tooManyRequests("Trop d'erreurs de PIN sur ce poste. Réessayez dans une heure "
          + "ou connectez-vous par e-mail.");
    }

    // 2. Le compte : matricule connu, essai compté avant la vérification
    String uid = matricule != null && matricule.matches(MATRICULE_REGEX)
        ? Objects.toString(store.read("secure/pinIndex/" + matricule), null) : null;
    if (uid == null || !Uids.isValid(uid)) {
      hasher.spendSameTime();
      deviceFailure(deviceId, now);
      throw ApiException.unauthorized(BAD_CREDENTIALS);
    }
    AccountCheck check = store.transact("secure/pins/" + uid, current -> countAttempt(current, now)).result();
    switch (check.state()) {
      case NO_PIN -> {
        hasher.spendSameTime();
        deviceFailure(deviceId, now);
        throw ApiException.unauthorized(BAD_CREDENTIALS);
      }
      case BLOCKED -> throw ApiException.tooManyRequests("Connexion par PIN bloquée pour ce compte après "
          + "trop d'erreurs. Un administrateur doit le débloquer ou définir un nouveau PIN.");
      case LOCKED -> throw ApiException.tooManyRequests("Trop d'erreurs de PIN. Réessayez dans "
          + Math.max(1, (check.lockedUntil() - now + 59_999) / 60_000) + " min.");
      case OK -> { }
    }

    // 3. Vérification du PIN
    AppPrincipal who = new AppPrincipal(uid, null, null, false, null, matricule);
    if (!hasher.matches(uid, pin == null ? "" : pin, check.hash())) {
      deviceFailure(deviceId, now);
      audit.record(who, "auth.pinFailure", "users/" + uid, Map.of("device", deviceId));
      throw ApiException.unauthorized(BAD_CREDENTIALS);
    }

    // 4. PIN correct : compteurs remis à zéro
    store.transact("secure/pins/" + uid, current -> {
      if (!(current instanceof Map<?, ?> raw)) {
        return current == null ? Tx.nothingToWrite(null) : Tx.abort(null);
      }
      Map<String, Object> p = Json.copyMap(raw);
      p.put("failures", 0L);
      p.put("lockLevel", 0L);
      p.put("lockedUntil", 0L);
      p.put("blocked", false);
      p.put("lastLoginAt", now);
      p.put("lastDevice", deviceId);
      return Tx.commit(p, null);
    });
    // 5. Compte toujours autorisé dans l'application, session limitée à ce poste
    UserProfile profile = directory.findProfile(uid).orElse(null);
    boolean approved = profile != null && Role.fromCode(profile.role()).isPresent()
        && !Boolean.FALSE.equals(profile.active());
    if (!approved) {
      throw ApiException.forbidden("Ce compte n'est pas autorisé. Contactez l'administrateur.");
    }

    store.update("secure/devices/" + deviceId, Map.of("lastUsedAt", now));
    String token = minter.createCustomToken(uid, Map.of(PinSessionGuard.DEVICE_CLAIM, deviceId));
    audit.record(new AppPrincipal(uid, profile.email(), null, false, profile.displayName(), matricule),
        "auth.pinLogin", "users/" + uid, Map.of("device", deviceId));
    return token;
  }

  /** Compte l'essai avant la vérification et pose les blocages. Sans effet de bord. */
  private static Tx<AccountCheck> countAttempt(Object current, long now) {
    if (current == null) {
      return Tx.nothingToWrite(new AccountCheck(AccountState.NO_PIN, null, 0));
    }
    if (!(current instanceof Map<?, ?> raw)) {
      return Tx.abort(new AccountCheck(AccountState.NO_PIN, null, 0));
    }
    Map<String, Object> p = Json.copyMap(raw);
    if (Json.truthy(p.get("blocked"))) {
      return Tx.abort(new AccountCheck(AccountState.BLOCKED, null, 0));
    }
    long until = num(p.get("lockedUntil"));
    if (until > now) {
      return Tx.abort(new AccountCheck(AccountState.LOCKED, null, until));
    }
    long failures = num(p.get("failures")) + 1;
    p.put("failures", failures);
    p.put("lastAttemptAt", now);
    if (failures >= BLOCK_AFTER) {
      p.put("blocked", true);
    } else if (failures % LOCK_AFTER == 0) {
      long level = num(p.get("lockLevel")) + 1;
      p.put("lockLevel", level);
      p.put("lockedUntil", now + FIRST_LOCK.toMillis() * (1L << Math.min(level - 1, 10)));
    }
    return Tx.commit(p, new AccountCheck(AccountState.OK, Objects.toString(p.get("hash"), null), 0));
  }

  /** Erreur sur ce poste : 20 dans l'heure → poste bloqué jusqu'à la fin de l'heure. */
  private void deviceFailure(String deviceId, long now) {
    store.transact("secure/deviceGuards/" + deviceId, current -> {
      Map<String, Object> g = current instanceof Map<?, ?> raw ? Json.copyMap(raw) : new LinkedHashMap<>();
      long start = num(g.get("windowStart"));
      long failures = num(g.get("failures"));
      if (now - start >= HOUR) {
        start = now;
        failures = 0;
      }
      failures++;
      g.put("windowStart", start);
      g.put("failures", failures);
      if (failures >= DEVICE_FAILURES_PER_HOUR) {
        g.put("lockedUntil", start + HOUR);
      }
      return Tx.commit(g, null);
    });
  }

  // ── Administration : postes ────────────────────────────────────────────────
  public EnrolledDevice enrollDevice(AppPrincipal actor, String label) {
    require(actor);
    requireEnabled();
    String name = label == null ? "" : label.trim();
    if (name.isEmpty() || name.length() > 60) {
      throw ApiException.badRequest("Nom du poste obligatoire (60 caractères maximum).");
    }
    String deviceId = randomToken(16);
    String secret = randomToken(32);
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("label", name);
    d.put("secretHash", sha256(secret));
    d.put("active", true);
    d.put("createdAt", clock.millis());
    d.put("createdBy", actor.uid());
    d.put("createdByName", actor.label());
    store.update("secure/devices", Map.of(deviceId, d));
    audit.record(actor, "device.enroll", "secure/devices/" + deviceId, Map.of("label", name));
    return new EnrolledDevice(deviceId, secret);
  }

  public List<DeviceView> listDevices(AppPrincipal actor) {
    require(actor);
    List<DeviceView> out = new ArrayList<>();
    if (store.read("secure/devices") instanceof Map<?, ?> all) {
      all.forEach((id, v) -> {
        if (v instanceof Map<?, ?> d) {
          out.add(new DeviceView(String.valueOf(id), Objects.toString(d.get("label"), ""),
              Boolean.TRUE.equals(d.get("active")), longOrNull(d.get("createdAt")),
              Objects.toString(d.get("createdByName"), null), longOrNull(d.get("revokedAt")),
              longOrNull(d.get("lastUsedAt"))));
        }
      });
    }
    out.sort(Comparator.comparing((DeviceView v) -> v.createdAt() == null ? 0L : v.createdAt()).reversed());
    return out;
  }

  /** Effet immédiat sur l'API et sur les nouvelles lectures et écritures (règles de la phase 2). */
  public void revokeDevice(AppPrincipal actor, String deviceId) {
    require(actor);
    if (deviceId == null || !deviceId.matches(DEVICE_ID_REGEX)) {
      throw ApiException.badRequest("Identifiant de poste invalide.");
    }
    long now = clock.millis();
    Boolean found = store.transact("secure/devices/" + deviceId, current -> {
      if (current == null) {
        return Tx.nothingToWrite(false);
      }
      if (!(current instanceof Map<?, ?> raw)) {
        return Tx.abort(false);
      }
      Map<String, Object> d = Json.copyMap(raw);
      d.put("active", false);
      d.put("revokedAt", now);
      d.put("revokedBy", actor.uid());
      return Tx.commit(d, true);
    }).result();
    if (!Boolean.TRUE.equals(found)) {
      throw ApiException.notFound("Poste introuvable.");
    }
    guard.invalidate(deviceId);
    audit.record(actor, "device.revoke", "secure/devices/" + deviceId, Map.of());
  }

  // ── Administration : PIN des comptes ───────────────────────────────────────
  public void setPin(AppPrincipal actor, String uid, String pin) {
    require(actor);
    requireEnabled();
    Uids.requireValid(uid);
    UserProfile profile = directory.findProfile(uid)
        .orElseThrow(() -> ApiException.notFound("Compte introuvable."));
    String matricule = profile.matricule() == null ? "" : profile.matricule().trim();
    if (matricule.isEmpty()) {
      throw ApiException.badRequest("Ce compte n'a pas de matricule : renseignez-le avant de définir un PIN.");
    }
    if (!matricule.matches(MATRICULE_REGEX)) {
      throw ApiException.badRequest("Matricule inutilisable pour le PIN (lettres, chiffres, - et _ uniquement).");
    }
    PinPolicy.check(pin, matricule);

    // Un matricule ne désigne qu'un seul compte
    String owner = store.transact("secure/pinIndex/" + matricule, current ->
        current == null || uid.equals(current) ? Tx.commit(uid, (String) null)
            : Tx.abort(String.valueOf(current))).result();
    if (owner != null) {
      throw ApiException.conflict("Ce matricule est déjà associé au PIN d'un autre compte.");
    }
    Object previous = store.read("secure/pins/" + uid + "/matricule");
    if (previous != null && !matricule.equals(previous)) {
      removeIndex(String.valueOf(previous), uid);
    }
    long now = clock.millis();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("hash", hasher.hash(uid, pin));
    p.put("matricule", matricule);
    p.put("setAt", now);
    p.put("setBy", actor.uid());
    p.put("failures", 0L);
    p.put("lockLevel", 0L);
    p.put("lockedUntil", 0L);
    p.put("blocked", false);
    store.update("secure/pins", Map.of(uid, p));
    audit.record(actor, "user.pinSet", "users/" + uid, Map.of());
  }

  public void removePin(AppPrincipal actor, String uid) {
    require(actor);
    Uids.requireValid(uid);
    Object matricule = store.read("secure/pins/" + uid + "/matricule");
    if (matricule != null) {
      removeIndex(String.valueOf(matricule), uid);
    }
    Map<String, Object> delete = new LinkedHashMap<>();
    delete.put(uid, null);
    store.update("secure/pins", delete);
    audit.record(actor, "user.pinRemove", "users/" + uid, Map.of());
  }

  public void unlockPin(AppPrincipal actor, String uid) {
    require(actor);
    Uids.requireValid(uid);
    Boolean found = store.transact("secure/pins/" + uid, current -> {
      if (current == null) {
        return Tx.nothingToWrite(false);
      }
      if (!(current instanceof Map<?, ?> raw)) {
        return Tx.abort(false);
      }
      Map<String, Object> p = Json.copyMap(raw);
      p.put("failures", 0L);
      p.put("lockLevel", 0L);
      p.put("lockedUntil", 0L);
      p.put("blocked", false);
      return Tx.commit(p, true);
    }).result();
    if (!Boolean.TRUE.equals(found)) {
      throw ApiException.notFound("Ce compte n'a pas de PIN.");
    }
    audit.record(actor, "user.pinUnlock", "users/" + uid, Map.of());
  }

  private void removeIndex(String matricule, String uid) {
    if (!matricule.matches(MATRICULE_REGEX)) {
      return;
    }
    store.transact("secure/pinIndex/" + matricule, current -> {
      if (current == null) {
        return Tx.nothingToWrite(null);
      }
      return uid.equals(current) ? Tx.commit(null, null) : Tx.abort(null);
    });
  }

  // ── Outils ─────────────────────────────────────────────────────────────────
  private void require(AppPrincipal actor) {
    if (!permissions.has(actor, Permission.USERS_MANAGE)) {
      throw ApiException.forbidden("Action réservée aux administrateurs.");
    }
  }

  private void requireEnabled() {
    if (hasher == null) {
      throw ApiException.unavailable("La connexion par PIN n'est pas activée sur le serveur.");
    }
  }

  private String randomToken(int bytes) {
    byte[] b = new byte[bytes];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean sameHash(String a, String b) {
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private static long num(Object v) {
    return v instanceof Number n ? n.longValue() : 0L;
  }

  private static Long longOrNull(Object v) {
    return v instanceof Number n ? n.longValue() : null;
  }
}
