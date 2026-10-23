package com.leoni.crimpingpro.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import com.leoni.crimpingpro.pin.PinSessionGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Vérifie le jeton Firebase de chaque requête /api/** (« Authorization: Bearer <jeton> »).
 *
 * <ul>
 *   <li>Pas d'en-tête : la requête continue sans identité, la configuration de sécurité répond 401.
 *   <li>Jeton refusé (mal formé, expiré, révoqué, autre projet, compte désactivé) : 401.
 *   <li>Session ouverte par PIN sur un poste révoqué, ou depuis plus de 12 h : 401.
 *   <li>Jeton valide mais compte sans profil validé ou désactivé : 403.
 * </ul>
 * Le jeton n'est jamais écrit dans les journaux.
 */
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
  private static final String BEARER = "Bearer ";

  private final IdTokenVerifier verifier;
  private final PrincipalResolver resolver;
  private final PinSessionGuard pinSessions;

  public FirebaseAuthenticationFilter(IdTokenVerifier verifier, PrincipalResolver resolver,
      PinSessionGuard pinSessions) {
    this.verifier = verifier;
    this.resolver = resolver;
    this.pinSessions = pinSessions;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER)) {
      chain.doFilter(request, response);
      return;
    }
    VerifiedToken token;
    try {
      token = verifier.verify(header.substring(BEARER.length()).trim());
    } catch (InvalidTokenException e) {
      SecurityContextHolder.clearContext();
      log.info("Jeton refusé ({}) sur {} {}", e.reason(), request.getMethod(), request.getRequestURI());
      if (e.reason() == InvalidTokenException.Reason.UNAVAILABLE) {
        ProblemResponses.write(response, 503, "Service indisponible",
            "La vérification de connexion est momentanément indisponible. Réessayez.");
      } else {
        ProblemResponses.write(response, 401, "Authentification requise",
            "Votre session n'est pas valide. Reconnectez-vous.");
      }
      return;
    }
    if (!pinSessions.allows(token)) {
      SecurityContextHolder.clearContext();
      log.info("Session PIN refusée (poste révoqué ou session expirée) sur {} {}", request.getMethod(),
          request.getRequestURI());
      ProblemResponses.write(response, 401, "Authentification requise",
          "Session PIN expirée ou poste révoqué. Reconnectez-vous.");
      return;
    }
    AppPrincipal principal = resolver.resolve(token);
    if (!principal.approved()) {
      log.info("Compte non autorisé uid={} sur {} {}", principal.uid(), request.getMethod(),
          request.getRequestURI());
      ProblemResponses.write(response, 403, "Accès refusé",
          "Ce compte n'est pas autorisé. Contactez l'administrateur.");
      return;
    }
    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().code().toUpperCase()));
    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
