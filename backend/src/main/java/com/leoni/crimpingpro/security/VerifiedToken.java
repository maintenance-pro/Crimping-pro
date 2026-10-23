package com.leoni.crimpingpro.security;

import java.util.Map;

/** Contenu d'un jeton d'identité Firebase dont la signature, l'expiration et le projet ont été vérifiés. */
public record VerifiedToken(String uid, String email, Map<String, Object> claims) {

  public VerifiedToken {
    claims = claims == null ? Map.of() : Map.copyOf(claims);
  }
}
