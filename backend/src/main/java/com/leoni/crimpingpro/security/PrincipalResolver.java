package com.leoni.crimpingpro.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Construit l'identité de l'appelant à partir du jeton vérifié et du profil users/{uid}.
 *
 * <p>Source du rôle pendant la transition : le profil en base, que les règles protègent
 * désormais (seul un administrateur ou le serveur peut écrire role et active). Le rôle est
 * recopié dans les custom claims pour la suite. Un cache de 30 secondes limite les lectures ;
 * il est vidé à chaque changement de rôle ou d'activation fait par l'API.
 */
@Component
public class PrincipalResolver {

  static final Duration TTL = Duration.ofSeconds(30);

  private record Cached(Optional<UserProfile> profile, Instant expires) {}

  private final UserDirectory directory;
  private final Clock clock;
  private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

  public PrincipalResolver(UserDirectory directory, Clock clock) {
    this.directory = directory;
    this.clock = clock;
  }

  public AppPrincipal resolve(VerifiedToken token) {
    Optional<UserProfile> profile = profile(token.uid());
    Role role = profile.flatMap(p -> Role.fromCode(p.role())).orElse(null);
    boolean active = profile.map(p -> !Boolean.FALSE.equals(p.active())).orElse(false);
    String name = profile.map(UserProfile::displayName).orElse(null);
    String matricule = profile.map(UserProfile::matricule).orElse(null);
    return new AppPrincipal(token.uid(), token.email(), role, active, name, matricule);
  }

  private Optional<UserProfile> profile(String uid) {
    Instant now = clock.instant();
    Cached hit = cache.get(uid);
    if (hit != null && hit.expires().isAfter(now)) {
      return hit.profile();
    }
    Optional<UserProfile> fresh = directory.findProfile(uid);
    cache.put(uid, new Cached(fresh, now.plus(TTL)));
    return fresh;
  }

  public void invalidate(String uid) {
    cache.remove(uid);
  }

  public void invalidateAll() {
    cache.clear();
  }
}
