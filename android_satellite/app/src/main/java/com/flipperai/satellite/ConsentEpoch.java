package com.flipperai.satellite;

import java.util.concurrent.atomic.AtomicInteger;

/** Revocation generation for asynchronous cloud-bound requests.
 * A queued request cannot regain authorization merely because consent is
 * toggled on again after it was captured. Never stores credentials or prompts.
 */
public final class ConsentEpoch {
  private final AtomicInteger generation=new AtomicInteger();
  public int snapshot(){return generation.get();}
  public void changed(){generation.incrementAndGet();}
  public boolean current(int snapshot){return generation.get()==snapshot;}
}
