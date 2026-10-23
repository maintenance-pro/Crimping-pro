package com.leoni.crimpingpro.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Clés au format des clés push() de Firebase (8 caractères d'horodatage puis 12 caractères),
 * mais déterministes : la même opération rejouée produit la même clé, donc jamais d'entrée en
 * double. L'ordre chronologique des clés est conservé pour les écrans qui trient par clé.
 */
public final class PushIds {

  private static final String CHARS =
      "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

  private PushIds() {}

  public static String of(long timestampMillis, String seed) {
    char[] id = new char[20];
    long t = timestampMillis;
    for (int i = 7; i >= 0; i--) {
      id[i] = CHARS.charAt((int) (t % 64));
      t /= 64;
    }
    byte[] hash = sha256(seed);
    for (int i = 0; i < 12; i++) {
      id[8 + i] = CHARS.charAt(hash[i] & 63);
    }
    return new String(id);
  }

  private static byte[] sha256(String s) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
