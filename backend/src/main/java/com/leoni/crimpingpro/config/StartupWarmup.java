package com.leoni.crimpingpro.config;

import com.leoni.crimpingpro.internal.DataSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ouvre la connexion à la Realtime Database dès le démarrage, en arrière-plan. Sans cela, le
 * premier appel après un démarrage (Cloud Run sans instance permanente) attend en plus cette
 * connexion : environ 10 s mesurées sur l'émulateur local, contre moins de 0,2 s ensuite.
 */
@Component
public class StartupWarmup {

  private static final Logger log = LoggerFactory.getLogger(StartupWarmup.class);

  private final DataSpace dataSpace;

  public StartupWarmup(DataSpace dataSpace) {
    this.dataSpace = dataSpace;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void warmUp() {
    Thread.ofVirtual().name("prechauffage-base").start(() -> {
      long start = System.nanoTime();
      try {
        dataSpace.current();
        log.info("Connexion à la base ouverte en {} ms", (System.nanoTime() - start) / 1_000_000);
      } catch (RuntimeException e) {
        log.warn("Préchauffage de la connexion à la base impossible : {}", e.getMessage());
      }
    });
  }
}
