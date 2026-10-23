package com.leoni.crimpingpro.web;

import com.leoni.crimpingpro.common.ApiException;
import com.leoni.crimpingpro.config.AppProperties;
import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.RateLimiter;
import com.leoni.crimpingpro.users.UserAdminService;
import com.leoni.crimpingpro.users.UserAdminService.NewUser;
import com.leoni.crimpingpro.web.dto.Dtos;
import com.leoni.crimpingpro.web.dto.Dtos.ActiveRequest;
import com.leoni.crimpingpro.web.dto.Dtos.CreateUserRequest;
import com.leoni.crimpingpro.web.dto.Dtos.CreatedUser;
import com.leoni.crimpingpro.web.dto.Dtos.PasswordRequest;
import com.leoni.crimpingpro.web.dto.Dtos.RoleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administration des comptes : remplace les écritures que faisait le navigateur. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final UserAdminService service;
  private final RateLimiter rateLimiter;
  private final AppProperties props;

  public AdminUserController(UserAdminService service, RateLimiter rateLimiter, AppProperties props) {
    this.service = service;
    this.rateLimiter = rateLimiter;
    this.props = props;
  }

  @PostMapping
  public ResponseEntity<CreatedUser> create(@AuthenticationPrincipal AppPrincipal actor,
      @Valid @RequestBody CreateUserRequest body) {
    limit(actor);
    String uid = service.createUser(actor,
        new NewUser(body.email(), body.password(), body.displayName(), body.matricule(), body.role()));
    return ResponseEntity.status(HttpStatus.CREATED).body(new CreatedUser(uid));
  }

  @PutMapping("/{uid}/role")
  public ResponseEntity<Void> role(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = Dtos.UID_PATTERN) String uid, @Valid @RequestBody RoleRequest body) {
    limit(actor);
    service.changeRole(actor, uid, body.role());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{uid}/active")
  public ResponseEntity<Void> active(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = Dtos.UID_PATTERN) String uid, @Valid @RequestBody ActiveRequest body) {
    limit(actor);
    service.setActive(actor, uid, body.active());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{uid}/password")
  public ResponseEntity<Void> password(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = Dtos.UID_PATTERN) String uid, @Valid @RequestBody PasswordRequest body) {
    limit(actor);
    service.resetPassword(actor, uid, body.password());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{uid}/sessions/revoke")
  public ResponseEntity<Void> revoke(@AuthenticationPrincipal AppPrincipal actor,
      @PathVariable @Pattern(regexp = Dtos.UID_PATTERN) String uid) {
    limit(actor);
    service.revokeSessions(actor, uid);
    return ResponseEntity.noContent().build();
  }

  private void limit(AppPrincipal actor) {
    if (!rateLimiter.tryAcquire("admin:" + actor.uid(), props.rateLimit().adminPerMinute(),
        Duration.ofMinutes(1))) {
      throw ApiException.tooManyRequests();
    }
  }
}
