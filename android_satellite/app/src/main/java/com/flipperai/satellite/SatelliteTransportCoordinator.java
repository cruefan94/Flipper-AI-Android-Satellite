package com.flipperai.satellite;

import java.util.EnumMap;

/** Explicit transport selection for the Android AI Satellite.
 *
 * A transport change never occurs automatically. Only one selected transport
 * carries a protocol session, and callers must reset that session whenever
 * the selection or active transport connection generation changes.
 *
 * BLE is the only connected adapter in the current Android application.
 * Wi-Fi and USB can be registered later, but merely selecting them cannot
 * silently fall back to BLE or send data over another link.
 */
public final class SatelliteTransportCoordinator {
    private final EnumMap<SatelliteTransport.Kind, SatelliteTransport> adapters =
        new EnumMap<>(SatelliteTransport.Kind.class);
    private SatelliteTransport.Kind selected=SatelliteTransport.Kind.BLE;
    private long generation;

    public synchronized void register(SatelliteTransport adapter) {
        if(adapter==null || adapter.kind()==null)
            throw new IllegalArgumentException("transport");
        SatelliteTransport old=adapters.put(adapter.kind(),adapter);
        if(old!=adapter && adapter.kind()==selected)generation++;
        if(old!=null && old!=adapter)old.close();
    }

    public synchronized SatelliteTransport.Kind selected() { return selected; }
    public synchronized long generation() { return generation; }

    /** Selection can change only from an explicit user action. */
    public synchronized boolean select(SatelliteTransport.Kind kind) {
        if(kind==null)throw new IllegalArgumentException("kind");
        if(!adapters.containsKey(kind))return false;
        if(kind!=selected) {
            SatelliteTransport previous=adapters.get(selected);
            if(previous!=null)previous.close();
            selected=kind;
            generation++;
        }
        return true;
    }

    public synchronized boolean isReady() {
        SatelliteTransport active=adapters.get(selected);
        return active!=null && active.isReady();
    }

    public synchronized boolean sendFrame(String frame) {
        if(frame==null || frame.isEmpty())return false;
        SatelliteTransport active=adapters.get(selected);
        return active!=null && active.isReady() && active.sendFrame(frame);
    }

    /** Advance the session token after a physical disconnect or reconnect. */
    public synchronized void invalidateSession() { generation++; }

    /** An old async response must never travel over a newly chosen link. */
    public synchronized boolean sendFrameIfCurrent(String frame,long token) {
        return generation==token && sendFrame(frame);
    }

    public synchronized void close() {
        for(SatelliteTransport adapter:adapters.values())adapter.close();
        adapters.clear();
        generation++;
    }
}
