package com.leoni.crimpingpro.security;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.leoni.crimpingpro.security.InvalidTokenException.Reason;

/**
 * Vérification par le SDK Firebase Admin : signature (clés publiques Google), expiration,
 * émetteur et audience = projet configuré.
 *
 * <p>checkRevoked=true : un jeton émis avant une révocation des sessions, ou appartenant à un
 * compte désactivé, est refusé même s'il n'a pas encore expiré. Cela coûte un appel au service
 * d'authentification par requête ; c'est le prix d'une révocation immédiate.
 */
public class FirebaseIdTokenVerifier implements IdTokenVerifier {

  private final FirebaseAuth auth;

  public FirebaseIdTokenVerifier(FirebaseAuth auth) {
    this.auth = auth;
  }

  @Override
  public VerifiedToken verify(String idToken) {
    if (idToken == null || idToken.isBlank()) {
      throw new InvalidTokenException(Reason.MISSING);
    }
    try {
      FirebaseToken token = auth.verifyIdToken(idToken, true);
      return new VerifiedToken(token.getUid(), token.getEmail(), token.getClaims());
    } catch (FirebaseAuthException e) {
      throw new InvalidTokenException(reasonOf(e.getAuthErrorCode()));
    } catch (IllegalArgumentException e) {
      throw new InvalidTokenException(Reason.MALFORMED);
    }
  }

  private static Reason reasonOf(AuthErrorCode code) {
    if (code == null) {
      return Reason.INVALID;
    }
    return switch (code) {
      case EXPIRED_ID_TOKEN -> Reason.EXPIRED;
      case REVOKED_ID_TOKEN -> Reason.REVOKED;
      case USER_DISABLED -> Reason.DISABLED;
      case CERTIFICATE_FETCH_FAILED -> Reason.UNAVAILABLE;
      default -> Reason.INVALID;
    };
  }
}
