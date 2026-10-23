package com.leoni.crimpingpro.support;

import com.leoni.crimpingpro.audit.AuditService;
import com.leoni.crimpingpro.security.AppPrincipal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingAuditService implements AuditService {

  public record Entry(String actorUid, String action, String entity, Map<String, Object> details) {}

  public final List<Entry> entries = new CopyOnWriteArrayList<>();

  @Override
  public void record(AppPrincipal actor, String action, String entity, Map<String, Object> details) {
    entries.add(new Entry(actor.uid(), action, entity, Map.copyOf(details)));
  }
}
