package com.leoni.crimpingpro.pin;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import java.util.Map;

/**
 * Jeton personnalisé signé par le compte de service du serveur. Sur Cloud Run, sans fichier de
 * clé, la signature passe par l'API IAM Credentials : le compte de service doit avoir le rôle
 * « Créateur de jetons du compte de service » sur lui-même (voir backend/README.md).
 */
public class FirebaseTokenMinter implements TokenMinter {

  private final FirebaseAuth auth;

  public FirebaseTokenMinter(FirebaseAuth auth) {
    this.auth = auth;
  }

  @Override
  public String createCustomToken(String uid, Map<String, Object> claims) {
    try {
      return auth.createCustomToken(uid, claims);
    } catch (FirebaseAuthException e) {
      throw new IllegalStateException("Création du jeton de session impossible", e);
    }
  }
}
