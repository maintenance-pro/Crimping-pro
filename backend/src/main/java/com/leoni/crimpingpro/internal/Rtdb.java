package com.leoni.crimpingpro.internal;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Lecture et attente synchrones sur la Realtime Database, avec délai maximal. */
public final class Rtdb {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private Rtdb() {}

  public static DataSnapshot read(DatabaseReference ref) {
    CompletableFuture<DataSnapshot> result = new CompletableFuture<>();
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        result.complete(snapshot);
      }

      @Override
      public void onCancelled(DatabaseError error) {
        result.completeExceptionally(error.toException());
      }
    });
    return await(result);
  }

  public static <T> T await(Future<T> future) {
    return await(future, TIMEOUT);
  }

  public static <T> T await(Future<T> future, Duration timeout) {
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Opération interrompue", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Base de données indisponible", e);
    }
  }
}
