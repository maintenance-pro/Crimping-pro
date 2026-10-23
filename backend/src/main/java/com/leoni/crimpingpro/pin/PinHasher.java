package com.leoni.crimpingpro.pin;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Empreinte d'un PIN : bcrypt (lent, salé) appliqué à HMAC-SHA256(poivre, uid:PIN).
 *
 * <p>Le poivre est un secret du serveur (Secret Manager), jamais stocké dans la base : même avec
 * une copie de la base, les empreintes ne permettent pas de retrouver les PIN par essais. Le PIN
 * n'est jamais stocké ni journalisé en clair.
 */
public class PinHasher {

  /** Empreinte d'un PIN quelconque : sert à garder le même temps de réponse si le matricule est inconnu. */
  private final String dummyHash;
  private final byte[] pepper;
  private final BCryptPasswordEncoder bcrypt;

  public PinHasher(String pepper, int cost) {
    this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    this.bcrypt = new BCryptPasswordEncoder(cost);
    this.dummyHash = bcrypt.encode(peppered("-", "000000"));
  }

  public String hash(String uid, String pin) {
    return bcrypt.encode(peppered(uid, pin));
  }

  public boolean matches(String uid, String pin, String hash) {
    if (hash == null || hash.isEmpty()) {
      bcrypt.matches(peppered(uid, pin), dummyHash);
      return false;
    }
    return bcrypt.matches(peppered(uid, pin), hash);
  }

  /** Même coût qu'une vérification réelle, sans résultat. */
  public void spendSameTime() {
    bcrypt.matches(peppered("-", "000001"), dummyHash);
  }

  private String peppered(String uid, String pin) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
      byte[] digest = mac.doFinal((uid + ":" + pin).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
