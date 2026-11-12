package com.flipperai.satellite;

/** Optional receive-only radio hardware contract. Not instantiated in the online player. */
public interface RadioReceiverPort {
  enum Mode { AM, FM, NFM, USB, LSB }
  final class Status {
    public final boolean connected;
    public final long frequencyHz;
    public final Float signalDbm; // null unless measured by actual receiver
    public final String error;
    public Status(boolean connected,long frequencyHz,Float signalDbm,String error){
      this.connected=connected;this.frequencyHz=frequencyHz;this.signalDbm=signalDbm;this.error=error;
    }
  }
  interface Listener {
    void onStatus(Status status);
    void onPcm(byte[] pcm,int sampleRate,int channels);
  }
  boolean available();
  void open(Listener listener) throws Exception;
  void tune(long frequencyHz,Mode modulation) throws Exception;
  void close();
}
