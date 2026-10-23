package com.leoni.crimpingpro.web.dto;

import com.leoni.crimpingpro.interventions.LabDecisionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Corps des décisions sur les bons. L'auteur, l'heure et le statut ne sont jamais lus ici : ils
 * viennent du jeton vérifié, de l'horloge du serveur et des règles de LabDecisionService.
 *
 * <p>requestId : identifiant choisi par le navigateur pour UNE décision, réutilisé tel quel en
 * cas de nouvel essai. mode : espace de données affiché par le navigateur (« test » ou
 * « production ») ; s'il ne correspond plus à celui du serveur, la décision est refusée.
 */
public final class DecisionDtos {

  private DecisionDtos() {}

  public static final String MODE = "^(test|production)$";
  public static final String REG = LabDecisionService.REG_REGEX;

  /** Fichier joint en data URL (photo de coupe, rapport) : contenu contrôlé par DataUrls. */
  public record Attachment(
      @NotBlank @Size(max = 200) String fileName,
      @PositiveOrZero Long fileSize,
      @Size(max = 100) String mimeType,
      @NotBlank String dataUrl,
      @PositiveOrZero Long uploadedAt) {
    @Override
    public String toString() {
      return "Attachment[fileName=" + fileName + ", fileSize=" + fileSize + "]";
    }
  }

  public record Connexion(@Size(max = 200) String refConnexion, @Size(max = 200) String sectionCable) {}

  public record ValidateRequest(
      @NotBlank @Pattern(regexp = LabDecisionService.REQUEST_ID_REGEX) String requestId,
      @Pattern(regexp = MODE) String mode,
      @Size(max = 100) String technicienNom,
      @Size(max = 32) String technicienMatricule,
      @Valid Connexion connexion,
      @Size(max = 20) Map<@Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String, @NotNull Boolean> conformites,
      @Valid Attachment coupeMetallo,
      String signatureDataUrl) {
    @Override
    public String toString() {
      return "ValidateRequest[requestId=" + requestId + "]";
    }
  }

  public record RejectRequest(
      @NotBlank @Pattern(regexp = LabDecisionService.REQUEST_ID_REGEX) String requestId,
      @Pattern(regexp = MODE) String mode,
      @NotBlank @Size(max = 2000) String reason) {}

  public record DeliverRequest(
      @NotBlank @Pattern(regexp = LabDecisionService.REQUEST_ID_REGEX) String requestId,
      @Pattern(regexp = MODE) String mode,
      @Size(max = 100) String tech2Name,
      @Size(max = 32) String tech2Matricule,
      @Valid Attachment reportData) {
    @Override
    public String toString() {
      return "DeliverRequest[requestId=" + requestId + "]";
    }
  }

  public record AutoValidateRequest(
      @NotBlank @Pattern(regexp = LabDecisionService.REQUEST_ID_REGEX) String requestId,
      @Pattern(regexp = MODE) String mode) {}

  /**
   * @param applied décision écrite par cette requête
   * @param replay la même requête avait déjà été appliquée (réponse perdue, nouvel essai)
   */
  public record DecisionResponse(String reg, String status, boolean applied, boolean replay,
      long decidedAt) {}
}
