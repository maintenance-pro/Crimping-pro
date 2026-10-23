package com.leoni.crimpingpro.support;

import com.leoni.crimpingpro.internal.Json;
import com.leoni.crimpingpro.internal.PushIds;
import com.leoni.crimpingpro.internal.Store;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Base en mémoire avec la sémantique utile de la Realtime Database : objets vides et valeurs
 * null non stockés, update() multi-chemins, transactions atomiques (ici sérialisées).
 */
public class InMemoryStore implements Store {

  private Map<String, Object> root = new LinkedHashMap<>();
  private int pushes;

  public synchronized void reset() {
    root = new LinkedHashMap<>();
    pushes = 0;
  }

  /** Écrit une valeur (comme set()). */
  public synchronized void set(String path, Object value) {
    write(path, clean(Json.copy(value)));
  }

  /** Enfants d'un nœud (vide si absent). */
  @SuppressWarnings("unchecked")
  public synchronized Map<String, Object> children(String path) {
    Object node = read(path);
    return node instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
  }

  @Override
  public synchronized Object read(String path) {
    Object current = root;
    for (String segment : segments(path)) {
      if (current instanceof Map<?, ?> m) {
        current = m.get(segment);
      } else if (current instanceof List<?> l && segment.matches("\\d+")) {
        int i = Integer.parseInt(segment);
        current = i < l.size() ? l.get(i) : null;
      } else {
        return null;
      }
      if (current == null) {
        return null;
      }
    }
    return Json.copy(current);
  }

  @Override
  public synchronized void update(String path, Map<String, Object> fields) {
    fields.forEach((key, value) -> write(path + "/" + key, clean(Json.copy(value))));
  }

  @Override
  public synchronized String push(String path, Map<String, Object> value) {
    String key = PushIds.of(System.currentTimeMillis(), "push-" + (++pushes));
    write(path + "/" + key, clean(Json.copy(value)));
    return key;
  }

  @Override
  public synchronized <R> Tx<R> transact(String path, Function<Object, Tx<R>> change) {
    Tx<R> tx = change.apply(read(path));
    if (tx.committed()) {
      write(path, clean(Json.copy(tx.newValue())));
    }
    return tx;
  }

  @SuppressWarnings("unchecked")
  private void write(String path, Object value) {
    List<String> segs = segments(path);
    Map<String, Object> parent = root;
    for (int i = 0; i < segs.size() - 1; i++) {
      Object next = parent.get(segs.get(i));
      Map<String, Object> child;
      if (next instanceof Map<?, ?>) {
        child = (Map<String, Object>) next;
      } else if (next instanceof List<?> list) {
        child = new LinkedHashMap<>();
        for (int j = 0; j < list.size(); j++) {
          if (list.get(j) != null) {
            child.put(String.valueOf(j), list.get(j));
          }
        }
        parent.put(segs.get(i), child);
      } else {
        if (value == null) {
          return;
        }
        child = new LinkedHashMap<>();
        parent.put(segs.get(i), child);
      }
      parent = child;
    }
    String last = segs.get(segs.size() - 1);
    if (value == null) {
      parent.remove(last);
    } else {
      parent.put(last, value);
    }
    root = (Map<String, Object>) clean(root);
    if (root == null) {
      root = new LinkedHashMap<>();
    }
  }

  /** Comme la base : null et objets vides ne sont pas stockés. */
  private static Object clean(Object value) {
    if (value instanceof Map<?, ?> m) {
      Map<String, Object> out = new LinkedHashMap<>();
      m.forEach((k, v) -> {
        Object c = clean(v);
        if (c != null) {
          out.put(String.valueOf(k), c);
        }
      });
      return out.isEmpty() ? null : out;
    }
    if (value instanceof List<?> l) {
      List<Object> out = new ArrayList<>();
      l.forEach(v -> out.add(clean(v)));
      return out.stream().allMatch(v -> v == null) ? null : out;
    }
    return value;
  }

  private static List<String> segments(String path) {
    return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
  }
}
