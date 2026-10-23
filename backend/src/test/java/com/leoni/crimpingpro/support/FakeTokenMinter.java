package com.leoni.crimpingpro.support;

import com.leoni.crimpingpro.pin.TokenMinter;
import java.util.Map;
import java.util.TreeMap;

/** Jeton personnalisé de test : « custom:<uid>:<revendications> ». */
public class FakeTokenMinter implements TokenMinter {

  @Override
  public String createCustomToken(String uid, Map<String, Object> claims) {
    return "custom:" + uid + ":" + new TreeMap<>(claims);
  }
}
