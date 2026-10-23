package com.leoni.crimpingpro.internal;

import java.util.Map;
import java.util.function.Function;

/**
 * Accès à la Realtime Database côté serveur (SDK Admin, qui contourne les règles : chaque
 * appelant vérifie les droits avant d'écrire). Les valeurs sont celles de la base :
 * Map, List, String, Long, Double, Boolean ou null.
 */
public interface Store {

  Object read(String path);

  /** Fusionne les champs donnés (comme update() côté navigateur). */
  void update(String path, Map<String, Object> fields);

  /** Ajoute un enfant avec une clé générée et renvoie cette clé. */
  String push(String path, Map<String, Object> value);

  /**
   * Transaction atomique sur un nœud. La fonction reçoit la valeur actuelle et renvoie la
   * nouvelle valeur à écrire, ou un abandon. Elle peut être rappelée plusieurs fois en cas de
   * conflit : elle ne doit avoir aucun effet de bord.
   *
   * <p>Attention : au premier passage, la valeur reçue peut être null alors que le nœud existe
   * sur le serveur (rien n'est encore lu). Pour null, la fonction ne doit donc jamais abandonner
   * en concluant « introuvable » : elle renvoie {@link Tx#nothingToWrite}. Si le nœud existe, le
   * serveur refuse cette écriture et la fonction est rappelée avec la vraie valeur.
   */
  <R> Tx<R> transact(String path, Function<Object, Tx<R>> change);

  /** Résultat d'une transaction : valeur à écrire (si committed) et résultat pour l'appelant. */
  record Tx<R>(Object newValue, R result, boolean committed) {

    public static <R> Tx<R> commit(Object newValue, R result) {
      return new Tx<>(newValue, result, true);
    }

    public static <R> Tx<R> abort(R result) {
      return new Tx<>(null, result, false);
    }

    /** Uniquement quand la valeur reçue est null : ne rien écrire (voir transact). */
    public static <R> Tx<R> nothingToWrite(R result) {
      return new Tx<>(null, result, true);
    }
  }
}
