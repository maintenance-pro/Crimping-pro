package com.leoni.crimpingpro.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
public class CoreConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Aucun compte local : l'identité vient uniquement de Firebase Authentication. Sans ce bean,
   * Spring Boot créerait un utilisateur en mémoire et écrirait son mot de passe dans les journaux.
   */
  @Bean
  UserDetailsService noLocalAccounts() {
    return username -> {
      throw new UsernameNotFoundException("Aucun compte local");
    };
  }
}
