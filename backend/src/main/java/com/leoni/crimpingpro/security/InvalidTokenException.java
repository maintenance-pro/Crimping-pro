package com.leoni.crimpingpro.security;

/** Jeton refusé. La raison sert au journal serveur ; le client reçoit un message générique. */
public class InvalidTokenException extends RuntimeException {

  public enum Reason { MISSING, MALFORMED, EXPIRED, REVOKED, DISABLED, INVALID, UNAVAILABLE }

  private final Reason reason;

  public InvalidTokenException(Reason reason) {
    super("Jeton refusé : " + reason);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
