package com.leoni.crimpingpro.web;

import com.leoni.crimpingpro.security.AppPrincipal;
import com.leoni.crimpingpro.security.Permission;
import com.leoni.crimpingpro.security.PermissionService;
import com.leoni.crimpingpro.web.dto.Dtos.MeResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Identité vérifiée de l'appelant : sert à contrôler la liaison frontend → API. */
@RestController
@RequestMapping("/api")
public class MeController {

  private final PermissionService permissions;

  public MeController(PermissionService permissions) {
    this.permissions = permissions;
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AppPrincipal principal) {
    List<String> granted = Arrays.stream(Permission.values())
        .filter(p -> permissions.has(principal, p))
        .map(Enum::name)
        .toList();
    return new MeResponse(principal.uid(), principal.email(), principal.role().code(),
        principal.active(), principal.displayName(), granted);
  }
}
