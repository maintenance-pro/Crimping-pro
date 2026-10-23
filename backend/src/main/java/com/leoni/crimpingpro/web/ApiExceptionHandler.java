package com.leoni.crimpingpro.web;

import com.leoni.crimpingpro.common.ApiException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erreurs au format RFC 9457, sans détail interne : pas de trace, pas de message technique.
 * Les erreurs inattendues reçoivent une référence, retrouvable dans les journaux du serveur.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApi(ApiException e) {
    return problem(e.status(), e.title(), e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> handleBadInput(IllegalArgumentException e) {
    log.info("Requête refusée : {}", e.getMessage());
    return problem(HttpStatus.BAD_REQUEST, "Requête invalide", "La requête est invalide.");
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ProblemDetail> handleUnavailable(IllegalStateException e) {
    String ref = UUID.randomUUID().toString();
    log.error("Service dépendant indisponible [ref {}]", ref, e);
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service indisponible",
        "Le service est momentanément indisponible. Réessayez. Référence : " + ref);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
    String ref = UUID.randomUUID().toString();
    log.error("Erreur interne [ref {}]", ref, e);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne",
        "Une erreur est survenue. Référence : " + ref);
  }

  /** Corps envoyé sans longueur annoncée et coupé par RequestSizeLimitFilter : 413, pas 400. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof RequestSizeLimitFilter.BodyTooLargeException) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(413),
            "La requête dépasse la taille autorisée. Réduisez la taille des fichiers joints.");
        body.setTitle("Trop volumineux");
        return ResponseEntity.status(413).body(body);
      }
    }
    return super.handleHttpMessageNotReadable(ex, headers, status, request);
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(title);
    return ResponseEntity.status(status).body(body);
  }
}
