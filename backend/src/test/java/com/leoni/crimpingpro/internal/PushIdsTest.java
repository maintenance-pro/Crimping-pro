package com.leoni.crimpingpro.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushIdsTest {

  @Test
  void meme_operation_meme_cle() {
    assertThat(PushIds.of(1_792_656_000_000L, "notification:req-1"))
        .isEqualTo(PushIds.of(1_792_656_000_000L, "notification:req-1"))
        .hasSize(20)
        .matches("^[-0-9A-Za-z_]{20}$");
    assertThat(PushIds.of(1_792_656_000_000L, "notification:req-1"))
        .isNotEqualTo(PushIds.of(1_792_656_000_000L, "notification:req-2"));
  }

  @Test
  void ordre_chronologique_comme_les_cles_push() {
    assertThat(PushIds.of(0, "x")).startsWith("--------");
    assertThat(PushIds.of(1_000, "z").compareTo(PushIds.of(2_000, "a"))).isNegative();
    assertThat(PushIds.of(1_792_656_000_000L, "z").compareTo(PushIds.of(1_792_656_000_001L, "a"))).isNegative();
  }
}
