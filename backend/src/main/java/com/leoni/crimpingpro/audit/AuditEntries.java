package com.leoni.crimpingpro.audit;

import com.leoni.crimpingpro.security.AppPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Format d'une entrée auditLog, identique à celui de l'application (timestamp, uid, userName,
 * action, entity, champs complémentaires), avec source = "api" pour distinguer le serveur.
 */
public final class AuditEntries {

  private AuditEntries() {}

  public static Map<String, Object> of(long at, AppPrincipal actor, String action, String entity,
      Map<String, Object> details) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("timestamp", at);
    entry.put("uid", actor.uid());
    entry.put("userName", actor.label());
    entry.put("action", action);
    entry.put("entity", entity);
    entry.put("source", "api");
    details.forEach(entry::putIfAbsent);
    return entry;
  }
}
