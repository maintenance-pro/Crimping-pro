package com.leoni.crimpingpro.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Réponses d'erreur au format RFC 9457 (application/problem+json) écrites hors des
 * contrôleurs (filtre, refus d'accès). Messages génériques : aucun détail interne.
 */
public final class ProblemResponses {

  private ProblemResponses() {}

  public static void write(HttpServletResponse response, int status, String title, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    if (status == HttpServletResponse.SC_UNAUTHORIZED) {
      response.setHeader("WWW-Authenticate", "Bearer");
    }
    response.getWriter().write("{\"type\":\"about:blank\",\"title\":" + json(title)
        + ",\"status\":" + status + ",\"detail\":" + json(detail) + "}");
  }

  private static String json(String s) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
