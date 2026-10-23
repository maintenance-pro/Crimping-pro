package com.leoni.crimpingpro.security;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.leoni.crimpingpro.internal.Rtdb;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** UserDirectory sur Firebase Authentication et la Realtime Database (SDK Admin). */
public class FirebaseUserDirectory implements UserDirectory {

  private final FirebaseAuth auth;
  private final FirebaseDatabase database;

  public FirebaseUserDirectory(FirebaseAuth auth, FirebaseDatabase database) {
    this.auth = auth;
    this.database = database;
  }

  @Override
  public Optional<UserProfile> findProfile(String uid) {
    DataSnapshot s = Rtdb.read(database.getReference("users").child(Uids.requireValid(uid)));
    if (!s.exists()) {
      return Optional.empty();
    }
    Object active = s.child("active").getValue();
    return Optional.of(new UserProfile(
        uid,
        text(s, "email"),
        text(s, "displayName"),
        text(s, "matricule"),
        text(s, "role"),
        active instanceof Boolean b ? b : null));
  }

  private static String text(DataSnapshot s, String key) {
    Object v = s.child(key).getValue();
    return v == null ? null : String.valueOf(v);
  }

  @Override
  public String createAuthUser(String email, String password, String displayName) {
    UserRecord.CreateRequest request = new UserRecord.CreateRequest()
        .setEmail(email)
        .setPassword(password)
        .setDisplayName(displayName)
        .setDisabled(false);
    try {
      return auth.createUser(request).getUid();
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
        throw new ConflictException();
      }
      throw new IllegalStateException("Création du compte impossible", e);
    }
  }

  @Override
  public void writeProfile(String uid, Map<String, Object> fields) {
    if (fields.containsKey("password") || fields.containsKey("pin")) {
      throw new IllegalArgumentException("Un secret ne doit jamais être écrit dans un profil");
    }
    Rtdb.await(database.getReference("users").child(Uids.requireValid(uid)).updateChildrenAsync(fields));
  }

  @Override
  public void setRoleClaim(String uid, String role) {
    try {
      Map<String, Object> claims = new HashMap<>(auth.getUser(Uids.requireValid(uid)).getCustomClaims());
      claims.put("role", role);
      auth.setCustomUserClaims(uid, claims);
    } catch (FirebaseAuthException e) {
      throw new IllegalStateException("Mise à jour du rôle dans le jeton impossible", e);
    }
  }

  @Override
  public void setPassword(String uid, String password) {
    update(new UserRecord.UpdateRequest(Uids.requireValid(uid)).setPassword(password));
  }

  @Override
  public void setDisabled(String uid, boolean disabled) {
    update(new UserRecord.UpdateRequest(Uids.requireValid(uid)).setDisabled(disabled));
  }

  private void update(UserRecord.UpdateRequest request) {
    try {
      auth.updateUser(request);
    } catch (FirebaseAuthException e) {
      throw new IllegalStateException("Mise à jour du compte impossible", e);
    }
  }

  @Override
  public void revokeSessions(String uid) {
    try {
      auth.revokeRefreshTokens(Uids.requireValid(uid));
    } catch (FirebaseAuthException e) {
      throw new IllegalStateException("Révocation des sessions impossible", e);
    }
  }

  @Override
  public void deleteAuthUser(String uid) {
    try {
      auth.deleteUser(Uids.requireValid(uid));
    } catch (FirebaseAuthException e) {
      throw new IllegalStateException("Suppression du compte impossible", e);
    }
  }
}
