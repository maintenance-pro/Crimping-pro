package com.leoni.crimpingpro.pin;

import java.util.Map;

/** Émet le jeton personnalisé Firebase qui ouvre la session après un PIN correct. */
public interface TokenMinter {

  String createCustomToken(String uid, Map<String, Object> claims);
}
