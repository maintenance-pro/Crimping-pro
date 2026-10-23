package com.leoni.crimpingpro.interventions;

import com.leoni.crimpingpro.audit.AuditEntries;
import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.internal.DataSpace;
import com.leoni.crimpingpro.internal.DataSpace.Space;
import com.leoni.crimpingpro.internal.Json;
import com.leoni.crimpingpro.internal.PushIds;
import com.leoni.crimpingpro.internal.Store;
import com.leoni.crimpingpro.internal.Store.Tx;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.Permission;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.security.Uids;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Décisions sur les bons : validation et refus par le laboratoire, livraison du dossier
 * (homologation / requalification) et validation automatique « réglage mécanique seul ».
 *
 * <p>Chaque décision est UNE transaction sur le nœud du bon :
 * <ul>
 *   <li>l'état est relu et vérifié dans la transaction : de deux décisions simultanées, une seule
 *       passe, l'autre reçoit 409 ;
 *   <li>le requestId choisi par le navigateur est conservé dans l'historique du bon : la même
 *       requête renvoyée (réseau coupé, réponse perdue, double clic) ne s'applique pas deux fois ;
 *   <li>les champs écrits sont ceux de l'application (db.validateIntervention,
 *       rejectIntervention, deliverDossier, autoValidateIntervention).
 * </ul>
 *
 * <p>Les suites (audit, notification à l'auteur du bon, déblocage de l'outil) sont écrites après
 * la transaction, à des clés déterministes : un rejeu les complète si besoin, sans doublon.
 */
@Service
public class LabDecisionService {

  private static final Logger log = LoggerFactory.getLogger(LabDecisionService.class);

  static final Set<String> FINAL_STATUSES = Set.of("validated", "rejected", "delivered");
  /** Marge sous la limite de 16 Mo par écriture de la base. */
  static final long MAX_NODE_CHARS = 15L * 1024 * 1024;
  static final String AUTO_NOTE =
      "Validé automatiquement — aucun changement conducteur (P-AR / E-AR). Sans passage laboratoire.";

  public static final String REG_REGEX = "^[A-Za-z0-9_-]{1,64}$";
  public static final String REQUEST_ID_REGEX = "^[A-Za-z0-9_-]{8,64}$";
  private static final Pattern REG = Pattern.compile(REG_REGEX);
  private static final Pattern REQUEST_ID = Pattern.compile(REQUEST_ID_REGEX);
  /** Clé d'outil utilisable comme segment de chemin (pas de « / », « . », « # », « $ », « [ », « ] »). */
  private static final Pattern TOOL_KEY = Pattern.compile("^[^./#$\\[\\]\\x00-\\x1F\\x7F]{1,200}$");

  public enum Action {
    VALIDATE("validated", "intervention.validate"),
    REJECT("rejected", "intervention.reject"),
    DELIVER("delivered", "intervention.deliverDossier"),
    AUTO_VALIDATE("validated", "intervention.autovalidate");

    final String status;
    final String auditAction;

    Action(String status, String auditAction) {
      this.status = status;
      this.auditAction = auditAction;
    }
  }

  /**
   * @param applied la décision a été écrite par cette requête
   * @param replay la même requête avait déjà été appliquée : rien n'est réécrit
   */
  public record Outcome(String reg, String status, boolean applied, boolean replay, long decidedAt) {}

  /** Saisie du laboratoire à la validation (fichiers déjà contrôlés par DataUrls). */
  public record LabForm(String technicienNom, String technicienMatricule, String refConnexion,
      String sectionCable, Map<String, Boolean> conformites, Map<String, Object> coupeMetallo,
      String signatureDataUrl) {}

  private enum Kind { APPLIED, REPLAY, REFUSED, UNPROCESSABLE, NOT_FOUND, TOO_LARGE }

  private record Result(Kind kind, String message, long decidedAt, Map<String, Object> bon) {
    static Result of(Kind kind) {
      return new Result(kind, null, 0, null);
    }

    static Result refused(String message) {
      return new Result(Kind.REFUSED, message, 0, null);
    }

    static Result unprocessable(String message) {
      return new Result(Kind.UNPROCESSABLE, message, 0, null);
    }
  }

  /** Règle d'une décision. Sans effet de bord : la transaction peut l'appeler plusieurs fois. */
  private interface Change {
    /** Refus, ou null si la décision est possible sur ce bon. */
    Result check(Map<String, Object> bon);

    /** Modifie la copie du bon et renvoie les entrées d'historique à ajouter. */
    List<Map<String, Object>> apply(Map<String, Object> bon);
  }

  private final Store store;
  private final DataSpace dataSpace;
  private final PermissionService permissions;
  private final Clock clock;

  public LabDecisionService(Store store, DataSpace dataSpace, PermissionService permissions, Clock clock) {
    this.store = store;
    this.dataSpace = dataSpace;
    this.permissions = permissions;
    this.clock = clock;
  }

  // ── Validation par le laboratoire ──────────────────────────────────────────
  public Outcome validate(AppPrincipal actor, String reg, String requestId, String mode, LabForm form) {
    require(actor, Permission.INTERVENTION_DECIDE);
    long now = clock.millis();
    return decide(actor, reg, requestId, mode, Action.VALIDATE, now, new Change() {
      @Override
      public Result check(Map<String, Object> bon) {
        Result decided = alreadyDecided(bon);
        if (decided != null) {
          return decided;
        }
        // Coupe obligatoire si changement conducteur (P-AR / E-AR) ou sous-type « coupe »
        boolean conducteur = Json.truthy(Json.get(bon, "crimping", "piecesChanged", "pAr"))
            || Json.truthy(Json.get(bon, "crimping", "piecesChanged", "eAr"));
        boolean required = conducteur || "coupe".equals(Json.text(bon, "crimping", "subType"));
        if (required && form.coupeMetallo() == null && !hasCoupe(bon)) {
          return Result.unprocessable("Coupe métallographique OBLIGATOIRE pour ce bon (changement "
              + "conducteur P-AR/E-AR). Joignez la photo/PDF avant de valider.");
        }
        return null;
      }

      @Override
      public List<Map<String, Object>> apply(Map<String, Object> bon) {
        long submittedAt = Json.longOr(bon, now, "sla", "submittedAt");
        Map<String, Object> lab = new LinkedHashMap<>();
        lab.put("technicienNom", orDefault(form.technicienNom(), actor.displayName()));
        lab.put("technicienMatricule", orDefault(form.technicienMatricule(), actor.matricule()));
        Map<String, Object> connexion = new LinkedHashMap<>();
        connexion.put("refConnexion", Objects.toString(form.refConnexion(), ""));
        connexion.put("sectionCable", Objects.toString(form.sectionCable(), ""));
        lab.put("connexion", connexion);
        lab.put("conformites", form.conformites() == null ? new LinkedHashMap<String, Object>()
            : new LinkedHashMap<String, Object>(form.conformites()));
        // Une coupe déjà jointe au bon est conservée si aucune nouvelle n'est envoyée
        Object coupe = form.coupeMetallo() != null ? form.coupeMetallo() : Json.get(bon, "lab", "coupeMetallo");
        if (coupe != null) {
          lab.put("coupeMetallo", coupe);
        }
        if (form.signatureDataUrl() != null) {
          lab.put("signatureDataUrl", form.signatureDataUrl());
        }
        lab.put("filledAt", now);
        lab.put("filledBy", signer(actor, true));
        lab.put("decision", "validated");

        bon.put("status", "validated");
        bon.put("lab", lab);
        Map<String, Object> sla = Json.child(bon, "sla");
        sla.put("decidedAt", now);
        sla.put("durationMs", now - submittedAt);
        Json.child(bon, "_indexes").put("status", "validated");
        // Homologation / requalification : pas encore verrouillé, la livraison du dossier suit
        bon.put("locked", !needsDossierStep(bon));
        bon.put("updatedAt", now);
        return List.of(historyEntry("validated", now, actor));
      }
    });
  }

  // ── Refus par le laboratoire ───────────────────────────────────────────────
  public Outcome reject(AppPrincipal actor, String reg, String requestId, String mode, String reason) {
    require(actor, Permission.INTERVENTION_DECIDE);
    if (reason == null || reason.isBlank()) {
      throw ApiException.badRequest("Le motif est obligatoire.");
    }
    String motif = reason.trim();
    long now = clock.millis();
    return decide(actor, reg, requestId, mode, Action.REJECT, now, new Change() {
      @Override
      public Result check(Map<String, Object> bon) {
        return alreadyDecided(bon);
      }

      @Override
      public List<Map<String, Object>> apply(Map<String, Object> bon) {
        long submittedAt = Json.longOr(bon, now, "sla", "submittedAt");
        Map<String, Object> lab = Json.child(bon, "lab");
        lab.put("decision", "rejected");
        lab.put("rejectReason", motif);
        lab.put("filledBy", signer(actor, false));
        bon.put("status", "rejected");
        Map<String, Object> sla = Json.child(bon, "sla");
        sla.put("decidedAt", now);
        sla.put("durationMs", now - submittedAt);
        Json.child(bon, "_indexes").put("status", "rejected");
        bon.put("updatedAt", now);
        Map<String, Object> entry = historyEntry("rejected", now, actor);
        entry.put("reason", motif);
        return List.of(entry);
      }
    });
  }

  // ── Étape 2 : livraison du dossier (homologation / requalification) ────────
  public Outcome deliver(AppPrincipal actor, String reg, String requestId, String mode,
      String tech2Name, String tech2Matricule, Map<String, Object> reportData) {
    require(actor, Permission.INTERVENTION_DECIDE);
    long now = clock.millis();
    return decide(actor, reg, requestId, mode, Action.DELIVER, now, new Change() {
      @Override
      public Result check(Map<String, Object> bon) {
        String status = Json.text(bon, "status");
        if ("delivered".equals(status)) {
          return Result.refused("Le dossier de ce bon a déjà été livré — rechargez la fiche.");
        }
        if (!"validated".equals(status)) {
          return Result.refused("Le bon doit être validé (étape 1) avant la livraison du dossier.");
        }
        if (!needsDossierStep(bon)) {
          return Result.refused("Étape livraison dossier réservée à Homologation et Requalification.");
        }
        return null;
      }

      @Override
      public List<Map<String, Object>> apply(Map<String, Object> bon) {
        Map<String, Object> lab = Json.child(bon, "lab");
        lab.put("deliveredAt", now);
        lab.put("deliveredBy", signer(actor, true));
        lab.put("tech2Name", orDefault(tech2Name, actor.displayName()));
        lab.put("tech2Matricule", orDefault(tech2Matricule, actor.matricule()));
        lab.put("reportInjected", true);
        // Sans nouveau rapport, un rapport déjà joint est conservé
        if (reportData != null) {
          lab.put("reportData", reportData);
        }
        bon.put("status", "delivered");
        Json.child(bon, "_indexes").put("status", "delivered");
        bon.put("locked", true);
        bon.put("updatedAt", now);
        Map<String, Object> entry = historyEntry("delivered", now, actor);
        entry.put("type", Objects.toString(Json.text(bon, "crimping", "type"), ""));
        return List.of(entry);
      }
    });
  }

  // ── Validation automatique : réglage mécanique seul, sans passage labo ─────
  public Outcome autoValidate(AppPrincipal actor, String reg, String requestId, String mode) {
    require(actor, Permission.INTERVENTION_AUTO_VALIDATE);
    long now = clock.millis();
    return decide(actor, reg, requestId, mode, Action.AUTO_VALIDATE, now, new Change() {
      @Override
      public Result check(Map<String, Object> bon) {
        Result decided = alreadyDecided(bon);
        if (decided != null) {
          return decided;
        }
        boolean reglage = Json.truthy(Json.get(bon, "crimping", "reglageMecanique"));
        String subType = Json.text(bon, "crimping", "subType");
        boolean companion = "coupe".equals(subType) || "binding".equals(subType)
            || "requalification".equals(Json.text(bon, "crimping", "type"));
        if (!reglage || companion) {
          return Result.unprocessable("Validation automatique réservée au réglage mécanique seul "
              + "(sans coupe, binding ni requalification). Soumettez le bon au laboratoire.");
        }
        return null;
      }

      @Override
      public List<Map<String, Object>> apply(Map<String, Object> bon) {
        Map<String, Object> submitted = historyEntry("submitted", now, actor);
        submitted.put("auto", true);
        Map<String, Object> validated = historyEntry("validated", now, actor);
        validated.put("auto", true);

        Map<String, Object> by = signer(actor, true);
        by.put("auto", true);
        Map<String, Object> lab = new LinkedHashMap<>();
        lab.put("autoValidated", true);
        lab.put("decision", "validated");
        lab.put("note", AUTO_NOTE);
        lab.put("filledAt", now);
        lab.put("filledBy", by);

        bon.put("status", "validated");
        bon.put("autoValidated", true);
        bon.put("lab", lab);
        Map<String, Object> sla = Json.child(bon, "sla");
        sla.put("submittedAt", now);
        sla.put("decidedAt", now);
        sla.put("durationMs", 0L);
        Json.child(bon, "_indexes").put("status", "validated");
        bon.put("locked", true);
        bon.put("updatedAt", now);
        return List.of(submitted, validated);
      }
    });
  }

  // ── Transaction commune ────────────────────────────────────────────────────
  private Outcome decide(AppPrincipal actor, String reg, String requestId, String mode,
      Action action, long now, Change change) {
    if (reg == null || !REG.matcher(reg).matches()) {
      throw ApiException.badRequest("Numéro de bon invalide.");
    }
    if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
      throw ApiException.badRequest("Identifiant de requête invalide.");
    }
    Space space = dataSpace.current();
    if (mode != null && !mode.equals(space.test() ? "test" : "production")) {
      throw ApiException.conflict("Le mode TEST / PRODUCTION a changé. Rechargez la page avant de décider.");
    }
    Tx<Result> tx = store.transact(space.path("interventions/" + reg),
        current -> attempt(current, actor, requestId, action, now, change));
    Result r = tx.result();
    return switch (r.kind()) {
      case NOT_FOUND -> throw ApiException.notFound("Bon introuvable.");
      case REFUSED -> throw ApiException.conflict(r.message());
      case UNPROCESSABLE -> throw ApiException.unprocessable(r.message());
      case TOO_LARGE -> throw ApiException.tooLarge(
          "Le bon dépasserait la taille maximale de la base. Réduisez la taille des fichiers joints.");
      case APPLIED, REPLAY -> {
        followUp(space, actor, reg, requestId, action, r);
        yield new Outcome(reg, action.status, r.kind() == Kind.APPLIED, r.kind() == Kind.REPLAY,
            r.decidedAt());
      }
    };
  }

  private static Tx<Result> attempt(Object current, AppPrincipal actor, String requestId,
      Action action, long now, Change change) {
    if (current == null) {
      // Rien de lu (premier passage) ou bon inexistant : voir Store.transact
      return Tx.nothingToWrite(Result.of(Kind.NOT_FOUND));
    }
    if (!(current instanceof Map<?, ?> raw)) {
      return Tx.abort(Result.of(Kind.NOT_FOUND));
    }
    Map<String, Object> bon = Json.copyMap(raw);
    Map<?, ?> previous = findRequest(bon.get("statusHistory"), requestId);
    if (previous != null) {
      boolean same = action.status.equals(previous.get("status")) && actor.uid().equals(previous.get("by"));
      if (!same) {
        return Tx.abort(Result.refused("Cette demande correspond à une autre décision. Rechargez la page."));
      }
      long at = previous.get("at") instanceof Number n ? n.longValue() : now;
      return Tx.abort(new Result(Kind.REPLAY, null, at, bon));
    }
    Result refusal = change.check(bon);
    if (refusal != null) {
      return Tx.abort(refusal);
    }
    List<Map<String, Object>> entries = change.apply(bon);
    entries.get(entries.size() - 1).put("requestId", requestId);
    entries.forEach(entry -> appendHistory(bon, entry));
    if (Json.estimateChars(bon) > MAX_NODE_CHARS) {
      return Tx.abort(Result.of(Kind.TOO_LARGE));
    }
    return Tx.commit(bon, new Result(Kind.APPLIED, null, now, bon));
  }

  // ── Suites de la décision (idempotentes) ───────────────────────────────────
  private void followUp(Space space, AppPrincipal actor, String reg, String requestId, Action action,
      Result r) {
    long at = r.decidedAt();
    Map<String, Object> bon = r.bon();

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("requestId", requestId);
    if (action == Action.DELIVER) {
      details.put("type", Objects.toString(Json.text(bon, "crimping", "type"), ""));
      details.put("tech2Name", Objects.toString(Json.text(bon, "lab", "tech2Name"), ""));
    }
    createOnce(space.path("auditLog"), at, "audit:" + action + ":" + requestId,
        AuditEntries.of(at, actor, action.auditAction, "interventions/" + reg, details));

    // Notification à l'auteur du bon (comme db.notifyCrimping)
    String author = Json.text(bon, "crimping", "filledBy", "uid");
    if (Uids.isValid(author)) {
      createOnce(space.path("notifications/" + author), at, "notification:" + requestId,
          notification(reg, action, bon, at));
    }

    if (action == Action.AUTO_VALIDATE) {
      unblockTool(space, actor, reg, requestId, bon, at);
    }
  }

  /** Comme db._unblockToolForBon : remet à « ok » les statuts qui bloquent l'outil. */
  private void unblockTool(Space space, AppPrincipal actor, String reg, String requestId,
      Map<String, Object> bon, long at) {
    String toolId = Json.text(bon, "tool", "toolId");
    if (toolId == null || toolId.isEmpty()) {
      return;
    }
    if (!TOOL_KEY.matcher(toolId).matches()) {
      log.warn("Outil non débloqué : identifiant d'outil invalide sur le bon {}", reg);
      return;
    }
    Object regValue = regValue(reg);
    try {
      Tx<Boolean> tx = store.transact(space.path("tools/" + toolId), current -> {
        if (current == null) {
          return Tx.nothingToWrite(false); // outil absent du catalogue : rien n'est créé
        }
        if (!(current instanceof Map<?, ?> raw)) {
          return Tx.abort(false);
        }
        Map<String, Object> tool = Json.copyMap(raw);
        if (String.valueOf(regValue).equals(String.valueOf(tool.get("lastRequalifBonReg")))) {
          return Tx.abort(false); // déjà débloqué pour ce bon (requête rejouée)
        }
        tool.put("requalifTimeStatus", "ok");
        tool.put("requalifStrokesStatus", "ok");
        tool.put("daysInUseStatus", "ok");
        tool.put("maintenanceStatus", "ok");
        tool.put("lastRequalifAt", at);
        tool.put("lastRequalifBonReg", regValue);
        return Tx.commit(tool, true);
      });
      if (Boolean.TRUE.equals(tx.result())) {
        createOnce(space.path("auditLog"), at, "audit:tool:" + requestId,
            AuditEntries.of(at, actor, "tool.unblock.auto", "tools/" + toolId, Map.of()));
      }
    } catch (RuntimeException e) {
      log.error("Déblocage de l'outil non fait pour le bon {}", reg, e);
    }
  }

  /** Écrit la valeur à une clé déterministe si elle n'existe pas encore. */
  private void createOnce(String parent, long at, String seed, Map<String, Object> value) {
    String path = parent + "/" + PushIds.of(at, seed);
    try {
      store.transact(path, current -> current == null ? Tx.commit(value, true) : Tx.abort(false));
    } catch (RuntimeException e) {
      log.error("Écriture complémentaire non faite sous {}", parent, e);
    }
  }

  private static Map<String, Object> notification(String reg, Action action, Map<String, Object> bon,
      long at) {
    String decision = action == Action.AUTO_VALIDATE ? "validated" : action.status;
    String message = switch (decision) {
      case "validated" -> "Ton bon N°" + reg + " a été validé ✅";
      case "rejected" -> "Ton bon N°" + reg + " a été refusé : "
          + Objects.toString(Json.text(bon, "lab", "rejectReason"), "");
      default -> "Ton bon N°" + reg + " : dossier livré 📁";
    };
    Map<String, Object> n = new LinkedHashMap<>();
    n.put("type", "intervention_" + decision);
    n.put("message", message);
    n.put("interventionId", regValue(reg));
    n.put("createdAt", at);
    n.put("read", false);
    return n;
  }

  // ── Outils ─────────────────────────────────────────────────────────────────
  private void require(AppPrincipal actor, Permission permission) {
    if (!permissions.has(actor, permission)) {
      throw ApiException.forbidden("Action non autorisée pour votre rôle.");
    }
  }

  private static Result alreadyDecided(Map<String, Object> bon) {
    String status = Json.text(bon, "status");
    if (status != null && FINAL_STATUSES.contains(status)) {
      String label = switch (status) {
        case "validated" -> "validé";
        case "rejected" -> "rejeté";
        default -> "livré";
      };
      return Result.refused("Ce bon a déjà été " + label + " — rechargez la fiche.");
    }
    return null;
  }

  private static boolean hasCoupe(Map<String, Object> bon) {
    return Json.truthy(Json.get(bon, "lab", "coupeMetallo", "downloadUrl"))
        || Json.truthy(Json.get(bon, "lab", "coupeMetallo", "dataUrl"));
  }

  private static boolean needsDossierStep(Map<String, Object> bon) {
    String type = Json.text(bon, "crimping", "type");
    return "homologation".equals(type) || "requalification".equals(type);
  }

  private static Map<String, Object> historyEntry(String status, long at, AppPrincipal actor) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("status", status);
    entry.put("at", at);
    entry.put("by", actor.uid());
    entry.put("byName", actor.label());
    return entry;
  }

  private static Map<String, Object> signer(AppPrincipal actor, boolean withMatricule) {
    Map<String, Object> by = new LinkedHashMap<>();
    by.put("uid", actor.uid());
    by.put("name", actor.label());
    if (withMatricule) {
      by.put("matricule", Objects.toString(actor.matricule(), ""));
    }
    return by;
  }

  private static String orDefault(String value, String fallback) {
    if (value != null && !value.isBlank()) {
      return value.trim();
    }
    return Objects.toString(fallback, "");
  }

  /** Le numéro de bon est stocké comme nombre par l'application (compteur counters/registre). */
  static Object regValue(String reg) {
    if (reg.matches("^[0-9]{1,15}$")) {
      return Long.parseLong(reg);
    }
    return reg;
  }

  static Map<?, ?> findRequest(Object history, String requestId) {
    Collection<?> entries = history instanceof List<?> l ? l
        : history instanceof Map<?, ?> m ? m.values() : List.of();
    for (Object e : entries) {
      if (e instanceof Map<?, ?> entry && requestId.equals(entry.get("requestId"))) {
        return entry;
      }
    }
    return null;
  }

  /** statusHistory est un tableau dans la base ; il peut revenir en objet s'il a des trous. */
  static void appendHistory(Map<String, Object> bon, Map<String, Object> entry) {
    Object history = bon.get("statusHistory");
    if (history instanceof Map<?, ?> map) {
      int last = -1;
      for (Object k : map.keySet()) {
        try {
          last = Math.max(last, Integer.parseInt(String.valueOf(k)));
        } catch (NumberFormatException ignored) {
          // clé non numérique : ignorée pour le calcul de l'indice suivant
        }
      }
      Map<String, Object> next = Json.copyMap(map);
      next.put(String.valueOf(last + 1), entry);
      bon.put("statusHistory", next);
      return;
    }
    List<Object> next = new ArrayList<>();
    if (history instanceof List<?> list) {
      next.addAll(list);
    }
    next.add(entry);
    bon.put("statusHistory", next);
  }
}
