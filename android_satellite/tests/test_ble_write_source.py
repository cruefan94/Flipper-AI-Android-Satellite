#!/usr/bin/env python3
"""Android BLE stall watchdog integration smoke audit; phone-level test still required."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"app/src/main/java/com/flipperai/satellite"
BLE=(JAVA/"BleSatellite.java").read_text()
GUARD=(JAVA/"BleWriteGuard.java").read_text()
STATE=(JAVA/"TransportState.java").read_text()

class WriteWatchdogSourceTests(unittest.TestCase):
    def test_gatt_setup_callbacks_cannot_race_disconnect(self):
        callback_names=("onConnectionStateChange","onServicesDiscovered",
                        "onDescriptorWrite","onMtuChanged")
        for callback in callback_names:
            section=BLE.split("void "+callback+"(",1)[1].split("    @Override",1)[0]
            self.assertIn("synchronized(BleSatellite.this) {",section)
            self.assertLess(section.index("synchronized(BleSatellite.this) {"),
                            section.index("if(link!=gatt"),callback)
        self.assertIn("public synchronized void disconnect()",BLE)
        self.assertIn("public synchronized void connect(BluetoothDevice device)",BLE)

    def test_connect_gatt_reference_published_before_first_callback(self):
        self.assertIn("public synchronized void connect(BluetoothDevice device)",BLE)
        self.assertIn("synchronized(BleSatellite.this) {",BLE)
        self.assertIn("if(link!=gatt)return;",BLE)
        self.assertIn("gatt=link;",BLE)
        self.assertLess(
            BLE.index("synchronized(BleSatellite.this) {",BLE.index("void onConnectionStateChange")),
            BLE.index("if(link!=gatt)return;",BLE.index("void onConnectionStateChange")),
        )

    def test_transport_state_visibility_between_ui_and_ble_callbacks(self):
        for signature in (
            "synchronized Phase phase()", "synchronized void begin()",
            "synchronized void connected()", "synchronized void subscribing()",
            "synchronized void ready()", "synchronized void disconnected()",
            "synchronized void mtu(int size)", "synchronized boolean canWrite(int bytes)",
            "synchronized int payloadLimit()",
        ):
            self.assertIn(signature, STATE)
        self.assertIn("private volatile boolean authorized,subscribed,mtuPending;",BLE)
        self.assertIn("private volatile BluetoothGatt gatt;",BLE)

    def test_ai_handshake_mtu_and_subscription_binding(self):
        self.assertIn("public static final int MIN_AI_ATT_PAYLOAD=32;",STATE)
        self.assertIn("state.hasHandshakeCapacity()",BLE)
        self.assertIn("BluetoothGattCharacteristic owner=d.getCharacteristic();",BLE)
        self.assertIn("!TX.equals(owner.getUuid())",BLE)
        self.assertIn("state.phase()!=TransportState.Phase.SUBSCRIBING",BLE)
        self.assertIn("|| mtuPending)return;",BLE)
        self.assertIn("try{discoveryStarted=link.discoverServices();}",BLE)
        self.assertIn("catch(SecurityException | IllegalStateException lostAccess)",BLE)
        self.assertIn("try{notificationEnabled=link.setCharacteristicNotification(tx,true);}",BLE)

    def test_single_gatt_write_and_timer(self):
        self.assertIn("private final BleWriteGuard writeGuard=new BleWriteGuard();",BLE)
        self.assertIn("GATT_WRITE_TIMEOUT_MS=6000L",BLE)
        self.assertIn("final int token=writeGuard.begin();",BLE)
        self.assertIn("ui.postDelayed(()->expireWrite(link,token),GATT_WRITE_TIMEOUT_MS);",BLE)
        self.assertIn("if(writeGuard.isOutstanding())return true;",BLE)
    def test_old_scan_timeout_never_stops_restarted_scan(self):
        self.assertIn("private int scanGeneration;",BLE)
        self.assertIn("final int startedGeneration=scanGeneration;",BLE)
        self.assertIn("if(startedGeneration==scanGeneration)stopScan();",BLE)
        self.assertIn("public synchronized void startScan()",BLE)
        self.assertIn("public synchronized void stopScan()",BLE)
        self.assertIn("scanGeneration++;",BLE)
        self.assertIn("catch(SecurityException | IllegalStateException failure)",BLE)
        self.assertIn("catch(SecurityException | IllegalStateException ignored)",BLE)

    def test_pairing_permission_loss_fails_closed(self):
        self.assertIn('events.status("Cannot inspect pairing: Bluetooth permission or adapter unavailable");',BLE)
        self.assertIn('events.status("Cannot start pairing: Bluetooth permission or adapter unavailable");',BLE)
        self.assertIn("try{return BondGate.canUseAiChannel(lastDevice.getBondState(),authorized);}",BLE)
        self.assertIn("return false; // Fail closed if trusted Android pairing state is unreadable.",BLE)

    def test_connection_setup_timeout_is_bound_to_the_same_gatt(self):
        self.assertIn("GATT_SETUP_TIMEOUT_MS=25000L",BLE)
        self.assertIn("ui.postDelayed(()->expireSetup(link),GATT_SETUP_TIMEOUT_MS)",BLE)
        self.assertIn("if(link!=gatt || isReady())return;",BLE)
        self.assertIn("catch(SecurityException | IllegalStateException failure)",BLE)

    def test_out_of_order_android_gatt_callbacks_fail_closed(self):
        for transition in ("Connected", "Subscribing", "Ready"):
            self.assertIn("private synchronized boolean transition"+transition+"()",BLE)
        self.assertIn("catch(IllegalStateException invalidTransition)",BLE)
        self.assertIn("if(!transitionConnected())",BLE)
        self.assertIn("if(!transitionSubscribing())",BLE)
        self.assertIn("if(!transitionReady())",BLE)

    def test_permission_revocation_mid_subscription_and_mtu_does_not_crash(self):
        self.assertIn('events.status("Bluetooth permission or link lost during subscription");',BLE)
        self.assertIn('events.status("Bluetooth permission or link lost during MTU request");',BLE)
        self.assertIn('if(!requested){mtuPending=false;disconnect();}',BLE)

    def test_revoked_bluetooth_permission_cannot_crash_disconnect_cleanup(self):
        self.assertIn("try{old.disconnect();}",BLE)
        self.assertIn("finally{",BLE)
        self.assertIn("try{old.close();}",BLE)
        self.assertIn("catch(SecurityException | IllegalStateException ignored)",BLE)

    def test_late_timers_do_not_close_new_connection(self):
        self.assertIn("if(link!=gatt || !writeGuard.isPending(token))return;",BLE)
        self.assertIn("writeGuard.reset();",BLE)
        self.assertIn("writeGuard.complete()",BLE)
    def test_failed_write_is_not_reported_as_success(self):
        self.assertIn("pending.addLast(bytes);return drain();",BLE)
        self.assertIn('events.status("AI GATT write failed to start");',BLE)
        self.assertIn('events.status("AI BLE write failed: "+code);',BLE)
        self.assertIn("catch(SecurityException | IllegalStateException invalidLink)",BLE)
        self.assertIn("writeGuard.complete();",BLE)
        self.assertIn('events.status("AI GATT write failed to start");',BLE)
        self.assertIn("disconnect();",BLE)
    def test_callback_guard_is_thread_safe(self):
        self.assertIn("synchronized int begin()",GUARD)
        self.assertIn("synchronized boolean complete()",GUARD)
        self.assertIn("synchronized void reset()",GUARD)

if __name__=="__main__":unittest.main()
