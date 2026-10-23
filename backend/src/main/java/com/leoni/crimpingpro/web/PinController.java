package com.leoni.crimpingpro.web;

import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.config.AppProperties;
import com.leoni.crimpingpro.pin.PinService;
import com.leoni.crimpingpro.pin.PinService.DeviceView;
import com.leoni.crimpingpro.pin.PinService.EnrolledDevice;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.RateLimiter;
import com.leoni.crimpingpro.web.dto.Dtos;
import com.leoni.crimpingpro.web.dto.PinDtos.DeviceRequest;
import com.leoni.crimpingpro.web.dto.PinDtos.EnrolledDeviceResponse;
import com.leoni.crimpingpro.web.dto.PinDtos.PinLoginRequest;
import com.leoni.crimpingpro.web.dto.PinDtos.PinLoginResponse;
import com.leoni.crimpingpro.web.dto.PinDtos.PinRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connexion par PIN sur poste enrôlé, et administration des postes et des PIN. */
@RestController
@RequestMapping("/api")
public class PinController {

  private final PinService service;
  private final RateLimiter rateLimiter;
  private final AppProperties props;

  public PinController(PinService service, RateLimiter rateLimiter, AppProperties props) {
    this.service = service;
    this.rateLimiter = rateLimiter;
    this.props = props;
  }

  /** Public (pas encore de session) : le poste et le PIN sont les seuls justificatifs. */
  @PostMapping("/pin/login")
  public PinLoginResponse login(@Valid @RequestBody PinLoginRequest body, HttpServletRequest request) {
    return new PinLoginResponse(service.login(clientIp(request), body.deviceId(), body.deviceSecret(),
        body.matricule(), body.pin()));
  }

  @PostMapping("/admin/devices")
  public ResponseEntity<EnrolledDeviceResponse> enroll(@AuthenticationPrincipal AppPrincipal actor,
      @Valid @RequestBody DeviceRequest body) {
    limit(actor);
    EnrolledDevice d = service.enrollDevice(actor, body.label());
    return ResponseEntity.status(HttpStatus.CREATED).body(new EnrolledDeviceResponse(d.deviceId(), d.deviceSecret()));
  }

  @GetMapping("/admin/devices")
  public List<DeviceView> devices(@AuthenticationPrincipal AppPrincipal actor) {
    limit(actor);
    return service.listDevices(actor);
  }

  @PostMapping("/admin/devices/{deviceId}/revoke")
  public ResponseEntity<Void> revoke(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = PinService.DEVICE_ID_REGEX) String deviceId) {
    limit(actor);
    service.revokeDevice(actor, deviceId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/admin/users/{uid}/pin")
  public ResponseEntity<Void> setPin(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = Dtos.UID_PATTERN) String uid, @Valid @RequestBody PinRequest body) {
    limit(actor);
    service.setPin(actor, uid, body.pin());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/admin/users/{uid}/pin")
  public ResponseEntity<Void> removePin(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = Dtos.UID_PATTERN) String uid) {
    limit(actor);
    service.removePin(actor, uid);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/admin/users/{uid}/pin/unlock")
  public ResponseEntity<Void> unlock(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = Dtos.UID_PATTERN) String uid) {
    limit(actor);
    service.unlockPin(actor, uid);
    return ResponseEntity.noContent().build();
  }

  /**
   * Adresse du client. Derrière le frontal Google (Cloud Run), l'adresse réelle est la dernière
   * de X-Forwarded-For ; les précédentes peuvent être fournies par le client. Cette limite par
   * adresse ne fait que compléter les limites par compte et par poste, enregistrées en base.
   */
  static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      String[] parts = forwarded.split(",");
      return parts[parts.length - 1].trim();
    }
    return request.getRemoteAddr();
  }

  private void limit(AppPrincipal actor) {
    if (!rateLimiter.tryAcquire("admin:" + actor.uid(), props.rateLimit().adminPerMinute(),
        Duration.ofMinutes(1))) {
      throw ApiException.tooManyRequests();
    }
  }
}
