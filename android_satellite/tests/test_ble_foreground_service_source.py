#!/usr/bin/env python3
"""BLE foreground-service and one-tap bonded-device regressions (source only)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
main=(SRC/"MainActivity.java").read_text()
service=(SRC/"BleConnectionService.java").read_text()
ble=(SRC/"BleSatellite.java").read_text()
recent=(SRC/"RecentFlipperStore.java").read_text()
manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
router=(SRC/"SatelliteTransportCoordinator.java").read_text()

class ServiceOwnedBleTests(unittest.TestCase):
    def test_one_single_service_owned_gatt(self):
        self.assertIn("transport=new BleSatellite(this,this);",service)
        self.assertNotIn("new BleSatellite(this,this)",main)
        self.assertIn("bleService=((BleConnectionService.LocalBinder)binder).service();",main)
        self.assertIn("bleService.attach(MainActivity.this);",main)
        self.assertIn("bleBound=bindService(",main)
        self.assertIn("transports.register(ble);",main)
        self.assertIn("transports.detach(ble);",main)
        self.assertNotIn("detachCurrentKind",main)
        self.assertNotIn("transports.close();",main)
        self.assertIn("bleService.detach(this);",main)
        self.assertIn("unbindService(bleBinding);",main)

    def test_foreground_type_and_explicit_start(self):
        self.assertIn('android:name=".BleConnectionService"',manifest)
        self.assertIn('android:foregroundServiceType="connectedDevice"',manifest)
        self.assertIn("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",manifest)
        self.assertIn("android.permission.BLUETOOTH_CONNECT",manifest)
        self.assertIn("new Intent(this,BleConnectionService.class)",main)
        self.assertIn(".setAction(BleConnectionService.ACTION_KEEP)",main)
        self.assertIn("startForegroundService(intent)",main)
        self.assertIn("startForeground(NOTICE_ID,notice())",service)
        self.assertIn("return START_NOT_STICKY",service)
        self.assertIn("ACTION_DISCONNECT",service)
        self.assertNotIn("ACTION_BOOT_COMPLETED",service)
        self.assertIn("stopFromUser()",main)
        self.assertIn("if(transport!=null)transport.manualDisconnect();",service)

    def test_history_is_gated_and_rebonded(self):
        self.assertIn("confirmedBondedDevice()",service)
        self.assertIn("if(device==null || device.getBondState()!=BluetoothDevice.BOND_BONDED)",recent)
        self.assertIn("getSharedPreferences(FILE,Context.MODE_PRIVATE)",recent)
        self.assertIn("for(BluetoothDevice device:adapter.getBondedDevices())",main)
        self.assertIn("record.address.equalsIgnoreCase(device.getAddress())",main)
        self.assertIn("RECONNECT  ",main)
        self.assertIn("CLEAR RECENT CONNECTIONS",main)
        self.assertIn("renderRecentConnections();",main)
        self.assertIn("MAX_RECENTS=3",(SRC/"RecentFlipperPolicy.java").read_text())

    def test_background_request_owner_remains_service_not_activity(self):
        self.assertIn("aiRequests.receive(frame)",service)
        self.assertIn("new BleAiRequestProcessor(",service)
        self.assertNotIn("listener.received(frame);",service)
        self.assertNotIn("processReceived(",main)
        self.assertIn("leaveAiSettingsScreen()",main)
        self.assertIn("public synchronized void detach(SatelliteTransport adapter)",router)
        self.assertIn("if(userDisconnected || token!=recoveryToken",ble)
        self.assertIn("if(userDisconnected || generation!=connectionGeneration",ble)

    def test_reliability_counters_and_redacted_copy(self):
        health=(SRC/"BleLinkHealth.java").read_text()
        self.assertIn("new BleLinkHealth()",service)
        self.assertIn("SystemClock.elapsedRealtime()",service)
        self.assertIn("linkHealth.recoveryAttempt()",service)
        self.assertIn("linkHealth.unexpectedDisconnect(now)",service)
        self.assertIn("linkHealth.userDisconnect(",service)
        self.assertIn("BleLinkHealth.Channel.CUSTOM_AI1",service)
        self.assertIn("BleLinkHealth.Channel.STOCK_RPC",service)
        self.assertIn("COPY BLUETOOTH RELIABILITY REPORT",main)
        self.assertIn("bleService.linkHealthReport()",main)
        self.assertIn("bleService.connectFromUser(device);",main)
        self.assertIn("Verified sessions:",health)
        self.assertIn("Unexpected verified-link drops:",health)
        self.assertNotIn("getAddress()",health)
        self.assertNotIn("getAddress()",service)

    def test_manifest_well_formed(self):
        ET.parse(ROOT/"app/src/main/AndroidManifest.xml")

if __name__=="__main__":unittest.main()
