package com.flipperai.satellite;

/** Receive-only receiver contract. The online player is implemented separately.
 * USB SDR and Flipper attachment backends are NOT implemented. */
public interface RadioReceiver {
  enum Source { ONLINE, USB_SDR, FLIPPER_ATTACHMENT }
  enum State { STOPPED, CONNECTING, BUFFERING, PLAYING, PAUSED, ERROR, UNAVAILABLE }
  final class Status {
    public final Source source;
    public final State state;
    public final String station;
    public final String message;
    public final Double signalDbm; // null when no real receiver measurement exists
    public Status(Source source,State state,String station,String message,Double signalDbm){
      this.source=source;this.state=state;this.station=station;this.message=message;this.signalDbm=signalDbm;
    }
  }
  Source source();
  Status status();
  void play(String stationId);
  void pause();
  void stop();
}
