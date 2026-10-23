package com.leoni.crimpingpro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** API sécurisée de Crimping Pro : vérifie les jetons Firebase et exécute les opérations sensibles. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CrimpingProApplication {

  public static void main(String[] args) {
    SpringApplication.run(CrimpingProApplication.class, args);
  }
}
