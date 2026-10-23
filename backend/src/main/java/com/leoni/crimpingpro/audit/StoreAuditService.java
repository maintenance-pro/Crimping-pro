package com.leoni.crimpingpro.audit;

import com.leoni.crimpingpro.internal.DataSpace;
import com.leoni.crimpingpro.internal.Store;
import com.leoni.crimpingpro.security.AppPrincipal;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Écrit dans auditLog, ou test_data/auditLog en mode TEST, comme l'application.
 * L'audit est écrit après l'opération : un échec est journalisé sans annuler l'opération faite.
 */
public class StoreAuditService implements AuditService {

  private static final Logger log = LoggerFactory.getLogger(StoreAuditService.class);

  private final Store store;
  private final DataSpace dataSpace;
  private final Clock clock;

  public StoreAuditService(Store store, DataSpace dataSpace, Clock clock) {
    this.store = store;
    this.dataSpace = dataSpace;
    this.clock = clock;
  }

  @Override
  public void record(AppPrincipal actor, String action, String entity, Map<String, Object> details) {
    try {
      store.push(dataSpace.current().path("auditLog"),
          AuditEntries.of(clock.millis(), actor, action, entity, details));
    } catch (RuntimeException e) {
      log.error("Audit non écrit : action={} entité={}", action, entity, e);
    }
  }
}
