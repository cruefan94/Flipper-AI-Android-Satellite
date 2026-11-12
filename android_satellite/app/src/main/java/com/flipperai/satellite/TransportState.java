package com.flipperai.satellite;

/** Pure Java BLE session gate, independently testable without Android runtime. */
public final class TransportState {
    public enum Phase { DISCONNECTED, CONNECTING, DISCOVERING, SUBSCRIBING, READY }
    /* Flipper AiBleBridge requires at least 32 usable ATT payload bytes. */
    public static final int MIN_AI_ATT_PAYLOAD=32;
    private Phase phase=Phase.DISCONNECTED;
    private int mtu=23;
    public synchronized Phase phase() { return phase; }
    public synchronized void begin() { phase=Phase.CONNECTING; mtu=23; }
    public synchronized void connected() { if(phase!=Phase.CONNECTING) throw new IllegalStateException("connect order");phase=Phase.DISCOVERING; }
    public synchronized void subscribing() { if(phase!=Phase.DISCOVERING) throw new IllegalStateException("discovery order");phase=Phase.SUBSCRIBING; }
    public synchronized void ready() { if(phase!=Phase.SUBSCRIBING) throw new IllegalStateException("subscription order");phase=Phase.READY; }
    public synchronized void disconnected() { phase=Phase.DISCONNECTED;mtu=23; }
    public synchronized void mtu(int size) { if(size>=23) mtu=size; }
    public synchronized boolean canWrite(int bytes) { return phase==Phase.READY && bytes>0 && bytes<=SatelliteProtocol.MAX_BYTES && bytes<=mtu-3; }
    public synchronized int payloadLimit() { return Math.min(SatelliteProtocol.MAX_BYTES,mtu-3); }
    public synchronized boolean hasHandshakeCapacity() { return payloadLimit()>=MIN_AI_ATT_PAYLOAD; }
}
