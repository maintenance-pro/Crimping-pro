package com.leoni.crimpingpro.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limiteur à fenêtre glissante, en mémoire.
 *
 * <p>Limite connue : chaque instance du serveur compte de son côté. Il suffit pour les
 * opérations d'administration ; les blocages du futur PIN seront enregistrés en base pour
 * valoir sur toutes les instances.
 */
@Component
public class RateLimiter {

  private final Clock clock;
  private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

  public RateLimiter(Clock clock) {
    this.clock = clock;
  }

  /** @return true si l'opération est autorisée (et comptée), false si la limite est atteinte */
  public boolean tryAcquire(String key, int limit, Duration window) {
    long now = clock.millis();
    long since = now - window.toMillis();
    Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (hits) {
      while (!hits.isEmpty() && hits.peekFirst() <= since) {
        hits.pollFirst();
      }
      if (hits.size() >= limit) {
        return false;
      }
      hits.addLast(now);
      return true;
    }
  }
}
