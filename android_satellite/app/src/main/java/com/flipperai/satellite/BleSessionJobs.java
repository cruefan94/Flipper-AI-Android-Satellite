package com.flipperai.satellite;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

/**
 * Tracks only jobs originating from a Flipper BLE AI session.
 * Connection loss cancels queued and in-flight requests without shutting down
 * the separate Android inference worker or unrelated model-import tasks.
 *
 * Cancellation interrupts cooperative engines; callers must also revoke active
 * HTTP connections and check their connection/consent epochs before any send.
 */
public final class BleSessionJobs {
  private final Executor worker;
  private final Set<FutureTask<?>> pending=ConcurrentHashMap.newKeySet();
  private volatile boolean closed;
  public BleSessionJobs(Executor worker) {
    if(worker==null)throw new IllegalArgumentException("Worker is required");
    this.worker=worker;
  }
  public boolean submit(Runnable operation) {
    if(operation==null)throw new IllegalArgumentException("Operation is required");
    if(closed)return false;
    FutureTask<Void> task=new FutureTask<Void>(operation,null) {
      @Override protected void done(){pending.remove(this);}
    };
    pending.add(task);
    if(closed){task.cancel(true);return false;}
    try {
      worker.execute(task);
      return true;
    }catch(RejectedExecutionException ignored) {
      task.cancel(true);
      return false;
    }
  }
  /** Called synchronously when the BLE link or its session is invalidated. */
  public void cancelAll() {
    for(FutureTask<?> task:pending)task.cancel(true);
  }
  /** Permanently rejects further BLE work after Activity shutdown. */
  public void close() {
    closed=true;
    cancelAll();
  }
}
