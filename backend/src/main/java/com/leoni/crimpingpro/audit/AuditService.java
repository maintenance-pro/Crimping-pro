package com.leoni.crimpingpro.audit;

import com.leoni.crimpingpro.security.AppPrincipal;
import java.util.Map;

/**
 * Journal d'audit écrit par le serveur : l'auteur vient du jeton vérifié, l'heure du serveur.
 * Ne jamais y mettre de mot de passe, de PIN ni de jeton.
 */
public interface AuditService {

  void record(AppPrincipal actor, String action, String entity, Map<String, Object> details);
}
