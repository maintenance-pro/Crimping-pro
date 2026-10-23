package com.leoni.crimpingpro.web;

import com.leoni.crimpingpro.config.AppProperties;
import com.leoni.crimpingpro.security.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Taille maximale du corps des requêtes /api/** : petite en général, plus grande pour les
 * décisions sur les bons (photo de coupe, rapport en base64). Vérifiée avant l'authentification,
 * y compris pour un envoi sans longueur annoncée (lecture interrompue au-delà de la limite).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final AppProperties props;

  public RequestSizeLimitFilter(AppProperties props) {
    this.props = props;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    long limit = path.startsWith("/api/interventions/")
        ? props.limits().decisionBodyBytes() : props.limits().defaultBodyBytes();
    long length = request.getContentLengthLong();
    if (length > limit) {
      ProblemResponses.write(response, 413, "Trop volumineux",
          "La requête dépasse la taille autorisée. Réduisez la taille des fichiers joints.");
      return;
    }
    chain.doFilter(length >= 0 ? request : new LimitedRequest(request, limit), response);
  }

  /** Levée quand un corps sans longueur annoncée dépasse la limite (traitée en 413). */
  public static class BodyTooLargeException extends IOException {
    public BodyTooLargeException() {
      super("Corps de requête trop volumineux");
    }
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {

    private final long limit;
    private ServletInputStream stream;

    LimitedRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        ServletInputStream in = super.getInputStream();
        stream = new ServletInputStream() {
          private long count;

          @Override
          public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
              check(1);
            }
            return b;
          }

          @Override
          public int read(byte[] buffer, int off, int len) throws IOException {
            int n = in.read(buffer, off, len);
            if (n > 0) {
              check(n);
            }
            return n;
          }

          private void check(int n) throws IOException {
            count += n;
            if (count > limit) {
              throw new BodyTooLargeException();
            }
          }

          @Override
          public boolean isFinished() {
            return in.isFinished();
          }

          @Override
          public boolean isReady() {
            return in.isReady();
          }

          @Override
          public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
          }
        };
      }
      return stream;
    }

    @Override
    public java.io.BufferedReader getReader() {
      throw new IllegalStateException("Lecture du corps par getInputStream uniquement");
    }
  }
}
