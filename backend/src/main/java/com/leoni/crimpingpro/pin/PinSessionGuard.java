package com.leoni.crimpingpro.pin;

import com.leoni.crimpingpro.config.AppProperties;
import com.leoni.crimpingpro.internal.Store;
import com.leoni.crimpingpro.security.VerifiedToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sessions ouvertes par PIN (jeton avec la revendication pin_device) : refusées si le poste a été
 * révoqué ou si la connexion date de plus de app.pin.session-hours. Mêmes conditions que les
 * règles de la base (database.rules.phase2.json). Cache de 30 secondes par poste, vidé à la
 * révocation faite par cette instance.
 */
@Component
public class PinSessionGuard {

  public static final String DEVICE_CLAIM = "pin_device";
  static final Duration TTL = Duration.ofSeconds(30);

  private record Cached(boolean active, Instant expires) {}

  private final Store store;
  private final Clock clock;
  private final AppProperties props;
  private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

  public PinSessionGuard(Store store, Clock clock, AppProperties props) {
    this.store = store;
    this.clock = clock;
    this.props = props;
  }

  public boolean allows(VerifiedToken token) {
    Object device = token.claims().get(DEVICE_CLAIM);
    if (device == null) {
      return true; // session ouverte par e-mail et mot de passe
    }
    if (!(device instanceof String id) || !id.matches(PinService.DEVICE_ID_REGEX)) {
      return false;
    }
    Object authTime = token.claims().get("auth_time");
    long maxAge = Duration.ofHours(props.pin().sessionHours()).toMillis();
    if (!(authTime instanceof Number seconds) || clock.millis() - seconds.longValue() * 1000 > maxAge) {
      return false;
    }
    return deviceActive(id);
  }

  private boolean deviceActive(String id) {
    Instant now = clock.instant();
    Cached hit = cache.get(id);
    if (hit != null && hit.expires().isAfter(now)) {
      return hit.active();
    }
    boolean active = Boolean.TRUE.equals(store.read("secure/devices/" + id + "/active"));
    cache.put(id, new Cached(active, now.plus(TTL)));
    return active;
  }

  public void invalidate(String deviceId) {
    cache.remove(deviceId);
  }
}
