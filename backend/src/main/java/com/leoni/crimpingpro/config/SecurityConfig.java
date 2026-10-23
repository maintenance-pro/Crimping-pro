package com.leoni.crimpingpro.config;

import com.leoni.crimpingpro.pin.PinSessionGuard;
import com.leoni.crimpingpro.security.FirebaseAuthenticationFilter;
import com.leoni.crimpingpro.security.IdTokenVerifier;
import com.leoni.crimpingpro.security.PrincipalResolver;
import com.leoni.crimpingpro.security.ProblemResponses;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Sécurité HTTP de l'API.
 *
 * <p>CSRF désactivé, choix documenté : l'identité voyage dans l'en-tête Authorization (jeton
 * Firebase), jamais dans un cookie. Un site tiers ne peut pas faire envoyer cet en-tête par le
 * navigateur de l'utilisateur, donc l'attaque CSRF ne s'applique pas. Si un cookie de session
 * était introduit un jour, la protection CSRF devrait être réactivée.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, IdTokenVerifier verifier,
      PrincipalResolver resolver, PinSessionGuard pinSessions) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            // Connexion par PIN : pas encore de session, le poste enrôlé et le PIN font foi
            .requestMatchers(HttpMethod.POST, "/api/pin/login").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll())
        .exceptionHandling(e -> e
            .authenticationEntryPoint((req, res, ex) -> ProblemResponses.write(res, 401,
                "Authentification requise", "Connexion requise."))
            .accessDeniedHandler((req, res, ex) -> ProblemResponses.write(res, 403,
                "Accès refusé", "Action non autorisée.")))
        .addFilterBefore(new FirebaseAuthenticationFilter(verifier, resolver, pinSessions),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /** Origines exactes autorisées (CORS_ALLOWED_ORIGINS). Vide = aucun navigateur tiers autorisé. */
  @Bean
  CorsConfigurationSource corsConfigurationSource(AppProperties props) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(props.cors().allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(false);
    config.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
