package com.leoni.crimpingpro.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages de l'application (application.yml + variables d'environnement).
 * Aucun secret ici : l'identifiant du projet et l'adresse de la base sont publics.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Firebase firebase, Cors cors, RateLimit rateLimit, Limits limits, Pin pin) {

  /**
   * Connexion par PIN sur poste enrôlé.
   *
   * @param pepper secret du serveur (Secret Manager, variable PIN_PEPPER, 32 caractères minimum) ;
   *     absent ou trop court : la connexion par PIN est désactivée
   * @param bcryptCost coût bcrypt des empreintes (11 ≈ 0,1 à 0,2 s par vérification)
   * @param sessionHours durée maximale d'une session ouverte par PIN
   */
  public record Pin(String pepper, int bcryptCost, int sessionHours) {
    public boolean enabled() {
      return pepper != null && pepper.length() >= 32;
    }

    @Override
    public String toString() {
      return "Pin[pepper=" + (enabled() ? "***" : "absent") + ", bcryptCost=" + bcryptCost
          + ", sessionHours=" + sessionHours + "]";
    }
  }

  /** enabled=false uniquement pour les tests, qui fournissent leurs propres implémentations. */
  public record Firebase(boolean enabled, String projectId, String databaseUrl) {}

  /** Origines autorisées à appeler l'API depuis un navigateur (vide = aucune). */
  public record Cors(List<String> allowedOrigins) {
    public Cors {
      allowedOrigins = allowedOrigins == null ? List.of()
          : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
  }

  /** Opérations autorisées par compte et par minute (administration, décisions sur les bons). */
  public record RateLimit(int adminPerMinute, int decisionsPerMinute) {}

  /** Taille maximale du corps des requêtes, en octets. */
  public record Limits(long defaultBodyBytes, long decisionBodyBytes) {}
}
