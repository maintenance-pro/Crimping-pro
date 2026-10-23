package com.leoni.crimpingpro.security;

import java.util.regex.Pattern;

/**
 * Contrôle des identifiants utilisés dans un chemin de la base. Un UID comme « x/role » ou
 * « ../… » désignerait un autre nœud : il est refusé avant toute lecture ou écriture.
 */
public final class Uids {

  public static final String REGEX = "^[A-Za-z0-9_-]{1,128}$";
  private static final Pattern PATTERN = Pattern.compile(REGEX);

  private Uids() {}

  public static boolean isValid(String uid) {
    return uid != null && PATTERN.matcher(uid).matches();
  }

  public static String requireValid(String uid) {
    if (!isValid(uid)) {
      throw new IllegalArgumentException("Identifiant invalide");
    }
    return uid;
  }
}
