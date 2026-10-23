package com.leoni.crimpingpro.security;

/** Profil users/{uid} tel que lu par le serveur. Ne contient jamais de mot de passe ni de PIN. */
public record UserProfile(
    String uid, String email, String displayName, String matricule, String role, Boolean active) {}
