package com.leoni.crimpingpro.web;

import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.config.AppProperties;
import com.leoni.crimpingpro.interventions.DataUrls;
import com.leoni.crimpingpro.interventions.LabDecisionService;
import com.leoni.crimpingpro.interventions.LabDecisionService.LabForm;
import com.leoni.crimpingpro.interventions.LabDecisionService.Outcome;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.RateLimiter;
import com.leoni.crimpingpro.web.dto.DecisionDtos;
import com.leoni.crimpingpro.web.dto.DecisionDtos.Attachment;
import com.leoni.crimpingpro.web.dto.DecisionDtos.AutoValidateRequest;
import com.leoni.crimpingpro.web.dto.DecisionDtos.DecisionResponse;
import com.leoni.crimpingpro.web.dto.DecisionDtos.DeliverRequest;
import com.leoni.crimpingpro.web.dto.DecisionDtos.RejectRequest;
import com.leoni.crimpingpro.web.dto.DecisionDtos.ValidateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Décisions sur les bons, à la place des écritures directes du navigateur. Toutes renvoient
 * 200 avec applied / replay, 409 si l'état du bon ne le permet plus, 422 si le bon est
 * incomplet (coupe obligatoire…), 404 si le bon n'existe pas.
 */
@RestController
@RequestMapping("/api/interventions/{reg}")
public class InterventionDecisionController {

  private final LabDecisionService service;
  private final RateLimiter rateLimiter;
  private final AppProperties props;
  private final Clock clock;

  public InterventionDecisionController(LabDecisionService service, RateLimiter rateLimiter,
      AppProperties props, Clock clock) {
    this.service = service;
    this.rateLimiter = rateLimiter;
    this.props = props;
    this.clock = clock;
  }

  @PostMapping("/validate")
  public DecisionResponse validate(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = DecisionDtos.REG) String reg, @Valid @RequestBody ValidateRequest body) {
    limit(actor);
    Map<String, Object> coupe = attachment(body.coupeMetallo(), "la photo de coupe");
    String signature = body.signatureDataUrl();
    if (signature != null) {
      DataUrls.check(signature, DataUrls.SIGNATURE_TYPES, DataUrls.MAX_SIGNATURE_BYTES, "la signature");
    }
    var connexion = body.connexion();
    LabForm form = new LabForm(body.technicienNom(), body.technicienMatricule(),
        connexion == null ? null : connexion.refConnexion(),
        connexion == null ? null : connexion.sectionCable(),
        body.conformites(), coupe, signature);
    return response(service.validate(actor, reg, body.requestId(), body.mode(), form));
  }

  @PostMapping("/reject")
  public DecisionResponse reject(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = DecisionDtos.REG) String reg, @Valid @RequestBody RejectRequest body) {
    limit(actor);
    return response(service.reject(actor, reg, body.requestId(), body.mode(), body.reason()));
  }

  @PostMapping("/deliver")
  public DecisionResponse deliver(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = DecisionDtos.REG) String reg, @Valid @RequestBody DeliverRequest body) {
    limit(actor);
    Map<String, Object> report = attachment(body.reportData(), "le rapport");
    return response(service.deliver(actor, reg, body.requestId(), body.mode(), body.tech2Name(),
        body.tech2Matricule(), report));
  }

  @PostMapping("/auto-validate")
  public DecisionResponse autoValidate(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = DecisionDtos.REG) String reg,
      @Valid @RequestBody AutoValidateRequest body) {
    limit(actor);
    return response(service.autoValidate(actor, reg, body.requestId(), body.mode()));
  }

  /** Même structure que l'application : fileName, fileSize, mimeType, dataUrl, uploadedAt. */
  private Map<String, Object> attachment(Attachment a, String label) {
    if (a == null) {
      return null;
    }
    DataUrls.check(a.dataUrl(), DataUrls.DOCUMENT_TYPES, DataUrls.MAX_FILE_BYTES, label);
    Map<String, Object> file = new LinkedHashMap<>();
    file.put("fileName", a.fileName());
    file.put("fileSize", a.fileSize() == null ? 0L : a.fileSize());
    file.put("mimeType", a.mimeType() == null ? "" : a.mimeType());
    file.put("dataUrl", a.dataUrl());
    file.put("uploadedAt", a.uploadedAt() == null ? clock.millis() : a.uploadedAt());
    return file;
  }

  private static DecisionResponse response(Outcome o) {
    return new DecisionResponse(o.reg(), o.status(), o.applied(), o.replay(), o.decidedAt());
  }

  private void limit(AppPrincipal actor) {
    if (!rateLimiter.tryAcquire("decision:" + actor.uid(), props.rateLimit().decisionsPerMinute(),
        Duration.ofMinutes(1))) {
      throw ApiException.tooManyRequests();
    }
  }
}
