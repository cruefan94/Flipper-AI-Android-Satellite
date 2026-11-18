package com.flipperai.satellite;
import java.util.concurrent.atomic.AtomicInteger;
/** Connection epoch invalidates in-flight replies when a BLE link changes.
 * Thread safe; does not claim to authenticate a connection.
 */
public final class SessionEpoch {
  private final AtomicInteger generation=new AtomicInteger(1);
  public int current() {return generation.get();}
  public int advance() {return generation.incrementAndGet();}
  public boolean matches(int snapshot) {return generation.get()==snapshot;}
}
