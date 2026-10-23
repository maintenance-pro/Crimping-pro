package com.leoni.crimpingpro.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.leoni.crimpingpro.audit.AuditService;
import com.leoni.crimpingpro.audit.StoreAuditService;
import com.leoni.crimpingpro.internal.DataSpace;
import com.leoni.crimpingpro.internal.FirebaseStore;
import com.leoni.crimpingpro.internal.Store;
import com.leoni.crimpingpro.pin.FirebaseTokenMinter;
import com.leoni.crimpingpro.pin.TokenMinter;
import com.leoni.crimpingpro.security.FirebaseIdTokenVerifier;
import com.leoni.crimpingpro.security.FirebaseUserDirectory;
import com.leoni.crimpingpro.security.IdTokenVerifier;
import com.leoni.crimpingpro.security.UserDirectory;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initialise le SDK Firebase Admin, utilisé UNIQUEMENT côté serveur.
 *
 * <p>Identifiants : l'identité de service fournie par l'hébergement (Application Default
 * Credentials, par exemple le compte de service de Cloud Run). Aucun fichier de clé dans le
 * dépôt ni dans l'image. Avec les émulateurs Firebase, un jeton fictif suffit.
 *
 * <p>Attention : le SDK Admin contourne les règles de sécurité de la base. Chaque opération
 * vérifie donc explicitement les droits (voir PermissionService et UserAdminService).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

  private static final String APP_NAME = "crimping-pro-api";

  @Bean
  FirebaseApp firebaseApp(AppProperties props) throws IOException {
    for (FirebaseApp existing : FirebaseApp.getApps()) {
      if (existing.getName().equals(APP_NAME)) {
        return existing;
      }
    }
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(credentials())
        .setProjectId(props.firebase().projectId())
        .setDatabaseUrl(props.firebase().databaseUrl())
        .build();
    return FirebaseApp.initializeApp(options, APP_NAME);
  }

  private static GoogleCredentials credentials() throws IOException {
    boolean emulators = System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null
        || System.getenv("FIREBASE_DATABASE_EMULATOR_HOST") != null;
    if (emulators) {
      // Jeton fictif accepté par les émulateurs. Une date d'expiration est obligatoire : sans
      // elle, le SDK Admin échoue en planifiant le renouvellement du jeton.
      return GoogleCredentials.create(new AccessToken("owner",
          Date.from(Instant.now().plus(Duration.ofDays(365)))));
    }
    return GoogleCredentials.getApplicationDefault();
  }

  @Bean
  FirebaseAuth firebaseAuth(FirebaseApp app) {
    return FirebaseAuth.getInstance(app);
  }

  @Bean
  FirebaseDatabase firebaseDatabase(FirebaseApp app) {
    return FirebaseDatabase.getInstance(app);
  }

  @Bean
  IdTokenVerifier idTokenVerifier(FirebaseAuth auth) {
    return new FirebaseIdTokenVerifier(auth);
  }

  @Bean
  UserDirectory userDirectory(FirebaseAuth auth, FirebaseDatabase database) {
    return new FirebaseUserDirectory(auth, database);
  }

  @Bean
  TokenMinter tokenMinter(FirebaseAuth auth) {
    return new FirebaseTokenMinter(auth);
  }

  @Bean
  Store store(FirebaseDatabase database) {
    return new FirebaseStore(database);
  }

  @Bean
  AuditService auditService(Store store, DataSpace dataSpace, Clock clock) {
    return new StoreAuditService(store, dataSpace, clock);
  }
}
