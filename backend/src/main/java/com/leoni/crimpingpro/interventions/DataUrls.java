package com.leoni.crimpingpro.interventions;

import com.leoni.crimpingpro.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contrôle des fichiers joints envoyés en data URL base64 (photo de coupe, rapport, signature),
 * comme l'application les stocke aujourd'hui dans la base.
 *
 * <p>Seuls les images et PDF sont acceptés, et le contenu doit correspondre au type annoncé :
 * un fichier HTML ou script déguisé en image est refusé (il pourrait être ouvert plus tard
 * depuis l'application). Taille maximale : une chaîne de la base est limitée à 10 Mo, d'où
 * 7 Mo par fichier une fois encodé en base64.
 */
public final class DataUrls {

  /** Taille maximale d'un fichier joint (avant encodage base64). */
  public static final int MAX_FILE_BYTES = 7 * 1024 * 1024;
  /** Taille maximale d'une signature dessinée. */
  public static final int MAX_SIGNATURE_BYTES = 1024 * 1024;

  public static final Set<String> DOCUMENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif",
      "image/webp", "image/bmp", "image/tiff", "image/heic", "image/heif", "application/pdf");
  public static final Set<String> SIGNATURE_TYPES = Set.of("image/png", "image/jpeg");

  private static final Pattern HEADER = Pattern.compile("^data:([a-z]+/[a-z0-9.+-]+);base64,");

  private static final Map<String, byte[][]> SIGNATURES = Map.of(
      "image/jpeg", new byte[][] {{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}},
      "image/png", new byte[][] {{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}},
      "image/gif", new byte[][] {ascii("GIF87a"), ascii("GIF89a")},
      "image/bmp", new byte[][] {ascii("BM")},
      "image/tiff", new byte[][] {{'I', 'I', 0x2A, 0x00}, {'M', 'M', 0x00, 0x2A}},
      "application/pdf", new byte[][] {ascii("%PDF-")});

  private DataUrls() {}

  /**
   * Vérifie la data URL et renvoie son type. Lève une erreur 400 avec un message clair sinon.
   *
   * @param label nom du fichier pour le message (« la photo de coupe »)
   */
  public static String check(String dataUrl, Set<String> allowed, int maxBytes, String label) {
    if (dataUrl == null || dataUrl.isEmpty()) {
      throw ApiException.badRequest("Fichier manquant : " + label + ".");
    }
    Matcher m = HEADER.matcher(dataUrl.length() > 200 ? dataUrl.substring(0, 200) : dataUrl);
    if (!m.find()) {
      throw ApiException.badRequest("Format de fichier non reconnu pour " + label + ".");
    }
    String type = m.group(1);
    if (!allowed.contains(type)) {
      throw ApiException.badRequest("Type de fichier non accepté pour " + label
          + " (images ou PDF uniquement).");
    }
    String payload = dataUrl.substring(m.end());
    long approxBytes = (long) payload.length() * 3 / 4;
    if (approxBytes > maxBytes) {
      throw ApiException.tooLarge("Fichier trop volumineux (" + label + ") : maximum "
          + (maxBytes / (1024 * 1024)) + " Mo.");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(payload);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("Fichier illisible : " + label + ".");
    }
    if (!contentMatches(type, bytes)) {
      throw ApiException.badRequest("Le contenu ne correspond pas au type annoncé pour " + label + ".");
    }
    return type;
  }

  static boolean contentMatches(String type, byte[] bytes) {
    switch (type) {
      case "image/webp" -> {
        return startsWith(bytes, ascii("RIFF"), 0) && startsWith(bytes, ascii("WEBP"), 8);
      }
      case "image/heic", "image/heif" -> {
        return startsWith(bytes, ascii("ftyp"), 4);
      }
      default -> {
        byte[][] signatures = SIGNATURES.get(type);
        return signatures != null && Arrays.stream(signatures).anyMatch(s -> startsWith(bytes, s, 0));
      }
    }
  }

  private static boolean startsWith(byte[] bytes, byte[] prefix, int offset) {
    if (bytes.length < offset + prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (bytes[offset + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }
}
