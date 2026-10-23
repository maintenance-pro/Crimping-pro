package com.leoni.crimpingpro.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  /** Horloge que le test fait avancer. */
  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-22T08:00:00Z");

    @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }

  @Test
  void refuse_au_dela_de_la_limite_puis_accepte_quand_la_fenetre_glisse() {
    MutableClock clock = new MutableClock();
    RateLimiter limiter = new RateLimiter(clock);
    Duration minute = Duration.ofMinutes(1);

    assertThat(limiter.tryAcquire("a", 2, minute)).isTrue();
    assertThat(limiter.tryAcquire("a", 2, minute)).isTrue();
    assertThat(limiter.tryAcquire("a", 2, minute)).isFalse();
    assertThat(limiter.tryAcquire("b", 2, minute)).as("autre clé, autre compteur").isTrue();

    clock.now = clock.now.plusSeconds(61);
    assertThat(limiter.tryAcquire("a", 2, minute)).isTrue();
  }
}
