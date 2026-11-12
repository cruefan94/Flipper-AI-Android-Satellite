package com.flipperai.satellite;

/** Common future-facing link abstraction: BLE, ESP32 Wi-Fi, or USB-C. */
public interface SatelliteTransport {
    enum Kind { BLE, WIFI, USB }
    Kind kind();
    boolean isReady();
    boolean sendFrame(String frame);
    void close();
}
