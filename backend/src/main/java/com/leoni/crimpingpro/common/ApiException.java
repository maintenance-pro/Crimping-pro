package com.leoni.crimpingpro.common;

import org.springframework.http.HttpStatus;

/** Refus métier avec un message destiné à l'utilisateur (aucun détail interne). */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String title;

  public ApiException(HttpStatus status, String title, String message) {
    super(message);
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** 401 : identification refusée (poste non enrôlé, matricule ou PIN incorrect). */
  public static ApiException unauthorized(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, "Connexion refusée", message);
  }

  /** 503 : fonction non activée sur ce serveur. */
  public static ApiException unavailable(String message) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service indisponible", message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, "Accès refusé", message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "Introuvable", message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, "Action impossible", message);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "Requête invalide", message);
  }

  /** 422 : la demande est comprise mais le bon ne permet pas cette décision (pièce manquante…). */
  public static ApiException unprocessable(String message) {
    return new ApiException(HttpStatus.valueOf(422), "Décision impossible", message);
  }

  /** 413 : fichier ou bon trop volumineux pour la base. */
  public static ApiException tooLarge(String message) {
    return new ApiException(HttpStatus.valueOf(413), "Trop volumineux", message);
  }

  public static ApiException tooManyRequests() {
    return tooManyRequests("Trop d'opérations en peu de temps. Réessayez dans une minute.");
  }

  public static ApiException tooManyRequests(String message) {
    return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes", message);
  }
}
