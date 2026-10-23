package com.leoni.crimpingpro.security;

/** Vérifie un jeton d'identité Firebase (« Authorization: Bearer … »). */
public interface IdTokenVerifier {

  /**
   * @throws InvalidTokenException si le jeton est absent, mal formé, expiré, révoqué, émis pour un
   *     autre projet, ou si le compte est désactivé
   */
  VerifiedToken verify(String idToken);
}
