package com.leoni.crimpingpro.support;

import com.leoni.crimpingpro.security.Uids;
import com.leoni.crimpingpro.security.UserDirectory;
import com.leoni.crimpingpro.security.UserProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Comptes et profils en mémoire, avec un historique des actions pour les vérifications. */
public class InMemoryUserDirectory implements UserDirectory {

  public final Map<String, Map<String, Object>> profiles = new HashMap<>();
  public final Map<String, String> authEmails = new HashMap<>();
  public final Map<String, String> roleClaims = new HashMap<>();
  public final Map<String, String> passwordsSet = new HashMap<>();
  public final Set<String> disabled = new HashSet<>();
  public final List<String> revoked = new ArrayList<>();
  public final List<String> deletedAuth = new ArrayList<>();
  public boolean failNextProfileWrite;
  private int counter;

  public synchronized void reset() {
    profiles.clear();
    authEmails.clear();
    roleClaims.clear();
    passwordsSet.clear();
    disabled.clear();
    revoked.clear();
    deletedAuth.clear();
    failNextProfileWrite = false;
  }

  public synchronized void seed(String uid, String role, Boolean active) {
    Map<String, Object> p = new HashMap<>();
    p.put("uid", uid);
    p.put("email", uid + "@leoni.test");
    p.put("displayName", "Nom " + uid);
    p.put("role", role);
    if (active != null) {
      p.put("active", active);
    }
    profiles.put(uid, p);
    authEmails.put(uid, uid + "@leoni.test");
  }

  @Override
  public synchronized Optional<UserProfile> findProfile(String uid) {
    Map<String, Object> p = profiles.get(Uids.requireValid(uid));
    if (p == null) {
      return Optional.empty();
    }
    return Optional.of(new UserProfile(uid, (String) p.get("email"), (String) p.get("displayName"),
        (String) p.get("matricule"), (String) p.get("role"), (Boolean) p.get("active")));
  }

  @Override
  public synchronized String createAuthUser(String email, String password, String displayName) {
    if (authEmails.containsValue(email)) {
      throw new ConflictException();
    }
    String uid = "nouveau" + (++counter);
    authEmails.put(uid, email);
    passwordsSet.put(uid, password);
    return uid;
  }

  @Override
  public synchronized void writeProfile(String uid, Map<String, Object> fields) {
    if (fields.containsKey("password") || fields.containsKey("pin")) {
      throw new IllegalArgumentException("secret interdit");
    }
    if (failNextProfileWrite) {
      failNextProfileWrite = false;
      throw new IllegalStateException("Base de données indisponible");
    }
    profiles.computeIfAbsent(Uids.requireValid(uid), k -> new HashMap<>()).putAll(fields);
  }

  @Override
  public synchronized void setRoleClaim(String uid, String role) {
    roleClaims.put(uid, role);
  }

  @Override
  public synchronized void setPassword(String uid, String password) {
    passwordsSet.put(uid, password);
  }

  @Override
  public synchronized void setDisabled(String uid, boolean value) {
    if (value) {
      disabled.add(uid);
    } else {
      disabled.remove(uid);
    }
  }

  @Override
  public synchronized void revokeSessions(String uid) {
    revoked.add(uid);
  }

  @Override
  public synchronized void deleteAuthUser(String uid) {
    authEmails.remove(uid);
    deletedAuth.add(uid);
  }
}
