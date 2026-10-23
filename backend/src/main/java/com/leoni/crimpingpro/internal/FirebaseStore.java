package com.leoni.crimpingpro.internal;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Store sur la Realtime Database via le SDK Firebase Admin. */
public class FirebaseStore implements Store {

  /** Une transaction peut transférer un bon complet (photos en base64) : délai plus large. */
  private static final Duration TRANSACTION_TIMEOUT = Duration.ofSeconds(30);

  private final FirebaseDatabase database;

  public FirebaseStore(FirebaseDatabase database) {
    this.database = database;
  }

  @Override
  public Object read(String path) {
    return Rtdb.read(database.getReference(path)).getValue();
  }

  @Override
  public void update(String path, Map<String, Object> fields) {
    Rtdb.await(database.getReference(path).updateChildrenAsync(fields));
  }

  @Override
  public String push(String path, Map<String, Object> value) {
    var ref = database.getReference(path).push();
    Rtdb.await(ref.setValueAsync(value));
    return ref.getKey();
  }

  @Override
  public <R> Tx<R> transact(String path, Function<Object, Tx<R>> change) {
    AtomicReference<Tx<R>> last = new AtomicReference<>();
    CompletableFuture<Tx<R>> done = new CompletableFuture<>();
    database.getReference(path).runTransaction(new Transaction.Handler() {
      @Override
      public Transaction.Result doTransaction(MutableData current) {
        Tx<R> tx = change.apply(current.getValue());
        last.set(tx);
        if (!tx.committed()) {
          return Transaction.abort();
        }
        current.setValue(tx.newValue());
        return Transaction.success(current);
      }

      @Override
      public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
        if (error != null) {
          done.completeExceptionally(error.toException());
        } else {
          // Dernier passage : celui qui a été écrit, ou celui qui a abandonné
          done.complete(last.get());
        }
      }
    }, false);
    return Rtdb.await(done, TRANSACTION_TIMEOUT);
  }
}
