package com.leoni.crimpingpro.web.dto;

import com.leoni.crimpingpro.security.Uids;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corps des requêtes et réponses. Seuls les champs listés sont lus : un uid, un rôle ou un
 * auteur ajouté par le navigateur est ignoré. L'identité vient toujours du jeton vérifié.
 */
public final class Dtos {

  private Dtos() {}

  public record CreateUserRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 128) String password,
      @NotBlank @Size(max = 100) String displayName,
      @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,32}$") String matricule,
      @NotBlank @Size(max = 32) String role) {
    @Override
    public String toString() {
      return "CreateUserRequest[email=" + email + ", role=" + role + ", password=***]";
    }
  }

  public record RoleRequest(@NotBlank @Size(max = 32) String role) {}

  public record ActiveRequest(@NotNull Boolean active) {}

  public record PasswordRequest(@NotBlank @Size(min = 8, max = 128) String password) {
    @Override
    public String toString() {
      return "PasswordRequest[password=***]";
    }
  }

  public record CreatedUser(String uid) {}

  public record MeResponse(String uid, String email, String role, boolean active, String displayName,
      List<String> permissions) {}

  /** Motif appliqué aux identifiants dans l'adresse (/api/admin/users/{uid}/…). */
  public static final String UID_PATTERN = Uids.REGEX;
}
