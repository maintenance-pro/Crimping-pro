package com.leoni.crimpingpro.internal;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Espace de données TEST / PROD. Reprend la règle de l'application : en mode TEST
 * (system/mode = "test"), les chemins de travail sont écrits sous test_data/, les catalogues
 * (outils, utilisateurs, stock…) restent communs. Le mode est relu à chaque opération pour
 * qu'une opération entière reste dans le même espace.
 */
@Component
public class DataSpace {

  /** Mêmes chemins que productionSwitch.NAMESPACED_PATHS dans l'application. */
  static final Set<String> NAMESPACED = Set.of(
      "interventions", "interventionMessages", "pdrChangeHistory", "pdrRequests",
      "notifications", "stockMovements", "auditLog", "stockImportHistory");

  private final Store store;

  public DataSpace(Store store) {
    this.store = store;
  }

  public Space current() {
    Object mode = store.read("system/mode");
    return new Space("test".equals(mode == null ? null : String.valueOf(mode)));
  }

  public record Space(boolean test) {

    public String path(String relative) {
      String first = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
      return test && NAMESPACED.contains(first) ? "test_data/" + relative : relative;
    }
  }
}
