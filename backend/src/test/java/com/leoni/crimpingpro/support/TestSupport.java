package com.leoni.crimpingpro.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Remplace Firebase par des doublures en mémoire (app.firebase.enabled=false). */
@TestConfiguration
public class TestSupport {

  @Bean
  FakeTokenVerifier fakeTokenVerifier() {
    return new FakeTokenVerifier();
  }

  @Bean
  InMemoryUserDirectory inMemoryUserDirectory() {
    return new InMemoryUserDirectory();
  }

  @Bean
  RecordingAuditService recordingAuditService() {
    return new RecordingAuditService();
  }

  @Bean
  InMemoryStore inMemoryStore() {
    return new InMemoryStore();
  }

  @Bean
  FakeTokenMinter fakeTokenMinter() {
    return new FakeTokenMinter();
  }
}
