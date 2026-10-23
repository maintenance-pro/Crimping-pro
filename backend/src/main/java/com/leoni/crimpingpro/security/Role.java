package com.leoni.crimpingpro.security;

import java.util.Arrays;
import java.util.Optional;

/** Rôles existants de Crimping Pro, avec leur code tel qu'il est stocké dans users/{uid}/role. */
public enum Role {
  SUPER_ADMIN("super_admin"),
  ADMIN("admin"),
  RESPONSABLE("responsable"),
  TL("tl"),
  CRIMP("crimp"),
  CRIMPING("crimping"),
  LABO("labo"),
  MAGASINIER("magasinier"),
  OPERATEUR("operateur"),
  VIEWER("viewer");

  private final String code;

  Role(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public boolean isAdmin() {
    return this == SUPER_ADMIN || this == ADMIN;
  }

  public static Optional<Role> fromCode(String code) {
    if (code == null) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
  }
}
