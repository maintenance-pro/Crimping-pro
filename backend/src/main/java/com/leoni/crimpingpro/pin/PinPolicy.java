package com.leoni.crimpingpro.pin;

import com.leoni.crimpingpro.common.ApiException;

/**
 * Règles d'un PIN : 6 à 8 chiffres, ni répétitif (111111, 121212, 123123), ni suite
 * (123456, 987654), ni tiré des chiffres du matricule. Un PIN reste faible par nature : la
 * sécurité vient surtout du poste enrôlé et de la limitation des essais (voir PinService).
 */
public final class PinPolicy {

  public static final int MIN_LENGTH = 6;
  public static final int MAX_LENGTH = 8;

  private PinPolicy() {}

  public static void check(String pin, String matricule) {
    if (pin == null || !pin.matches("^[0-9]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$")) {
      throw ApiException.badRequest("Le PIN doit comporter de " + MIN_LENGTH + " à " + MAX_LENGTH + " chiffres.");
    }
    if (repeatsShortBlock(pin)) {
      throw ApiException.badRequest("PIN trop simple : évitez les répétitions (111111, 121212, 123123…).");
    }
    if (isSequence(pin)) {
      throw ApiException.badRequest("PIN trop simple : évitez les suites (123456, 987654…).");
    }
    // Seulement pour un matricule d'au moins 4 chiffres (un ou deux chiffres en commun ne prouvent rien)
    String digits = matricule == null ? "" : matricule.replaceAll("[^0-9]", "");
    if (digits.length() >= 4 && (pin.contains(digits) || digits.contains(pin))) {
      throw ApiException.badRequest("Le PIN ne doit pas reprendre le matricule.");
    }
  }

  /** Le PIN est un bloc de 1 à 3 chiffres répété (éventuellement tronqué à la fin). */
  static boolean repeatsShortBlock(String pin) {
    for (int size = 1; size <= 3; size++) {
      boolean repeats = true;
      for (int i = size; i < pin.length(); i++) {
        if (pin.charAt(i) != pin.charAt(i - size)) {
          repeats = false;
          break;
        }
      }
      if (repeats) {
        return true;
      }
    }
    return false;
  }

  static boolean isSequence(String pin) {
    int step = pin.charAt(1) - pin.charAt(0);
    if (step != 1 && step != -1) {
      return false;
    }
    for (int i = 2; i < pin.length(); i++) {
      if (pin.charAt(i) - pin.charAt(i - 1) != step) {
        return false;
      }
    }
    return true;
  }
}
