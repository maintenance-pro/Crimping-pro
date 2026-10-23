package com.leoni.crimpingpro.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lecture prudente des valeurs de la base (Map, List, String, Long, Double, Boolean). */
public final class Json {

  private Json() {}

  /** Copie profonde : la copie peut être modifiée sans toucher à l'original. */
  public static Map<String, Object> copyMap(Map<?, ?> source) {
    Map<String, Object> out = new LinkedHashMap<>();
    source.forEach((k, v) -> out.put(String.valueOf(k), copy(v)));
    return out;
  }

  public static Object copy(Object value) {
    if (value instanceof Map<?, ?> m) {
      return copyMap(m);
    }
    if (value instanceof List<?> l) {
      List<Object> out = new ArrayList<>(l.size());
      l.forEach(v -> out.add(copy(v)));
      return out;
    }
    return value;
  }

  public static Object get(Map<String, Object> root, String... path) {
    Object current = root;
    for (String key : path) {
      if (!(current instanceof Map<?, ?> m)) {
        return null;
      }
      current = m.get(key);
    }
    return current;
  }

  /** Texte d'une valeur simple, null si absente ou si c'est un objet. */
  public static String text(Map<String, Object> root, String... path) {
    Object v = get(root, path);
    return v == null || v instanceof Map || v instanceof List ? null : String.valueOf(v);
  }

  /** Nombre entier (comme `x || fallback` en JavaScript : 0 ou absent donne fallback). */
  public static long longOr(Map<String, Object> root, long fallback, String... path) {
    Object v = get(root, path);
    if (v instanceof Number n && n.longValue() != 0) {
      return n.longValue();
    }
    return fallback;
  }

  /** Sous-objet modifiable, créé s'il est absent ou n'est pas un objet. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> child(Map<String, Object> parent, String key) {
    Object v = parent.get(key);
    if (v instanceof Map<?, ?>) {
      return (Map<String, Object>) v;
    }
    Map<String, Object> created = new LinkedHashMap<>();
    parent.put(key, created);
    return created;
  }

  /** Valeur « vraie » au sens de JavaScript, comme les tests du navigateur (!!x). */
  public static boolean truthy(Object v) {
    if (v == null) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof Number n) {
      double d = n.doubleValue();
      return d != 0 && !Double.isNaN(d);
    }
    if (v instanceof String s) {
      return !s.isEmpty();
    }
    return true;
  }

  /** Taille approximative en caractères JSON, pour refuser un nœud trop gros avant l'écriture. */
  public static long estimateChars(Object value) {
    if (value instanceof Map<?, ?> m) {
      long total = 2;
      for (Map.Entry<?, ?> e : m.entrySet()) {
        total += String.valueOf(e.getKey()).length() + 4 + estimateChars(e.getValue());
      }
      return total;
    }
    if (value instanceof List<?> l) {
      long total = 2;
      for (Object v : l) {
        total += 1 + estimateChars(v);
      }
      return total;
    }
    if (value instanceof String s) {
      return s.length() + 2L;
    }
    return 24;
  }
}
