package com.leoni.crimpingpro.support;

import com.leoni.crimpingpro.security.IdTokenVerifier;
import com.leoni.crimpingpro.security.InvalidTokenException;
import com.leoni.crimpingpro.security.InvalidTokenException.Reason;
import com.leoni.crimpingpro.security.VerifiedToken;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Jetons de test : « jeton-<uid> » est valide ; expired / revoked / disabled simulent les refus. */
public class FakeTokenVerifier implements IdTokenVerifier {

  private final Map<String, VerifiedToken> valid = new ConcurrentHashMap<>();

  public String tokenFor(String uid) {
    return tokenFor(uid, Map.of());
  }

  /** Jeton avec revendications (ex. session PIN : pin_device, auth_time). */
  public String tokenFor(String uid, Map<String, Object> claims) {
    String token = "jeton-" + uid + (claims.isEmpty() ? "" : "-" + Math.abs(claims.hashCode()));
    valid.put(token, new VerifiedToken(uid, uid + "@leoni.test", claims));
    return token;
  }

  @Override
  public VerifiedToken verify(String idToken) {
    if (idToken == null || idToken.isBlank()) {
      throw new InvalidTokenException(Reason.MISSING);
    }
    switch (idToken) {
      case "expired" -> throw new InvalidTokenException(Reason.EXPIRED);
      case "revoked" -> throw new InvalidTokenException(Reason.REVOKED);
      case "disabled" -> throw new InvalidTokenException(Reason.DISABLED);
      default -> { }
    }
    VerifiedToken token = valid.get(idToken);
    if (token == null) {
      throw new InvalidTokenException(Reason.INVALID);
    }
    return token;
  }
}
