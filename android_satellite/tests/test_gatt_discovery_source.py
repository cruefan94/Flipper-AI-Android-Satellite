#!/usr/bin/env python3
"""Android BLE source regression tests for redacted GATT runtime evidence."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/flipperai/satellite"
BLE = (JAVA / "BleSatellite.java").read_text()
MAIN = (JAVA / "MainActivity.java").read_text()
SNAPSHOT = (JAVA / "GattDiscoverySnapshot.java").read_text()


class GattDiscoveryWiringTests(unittest.TestCase):
    def test_only_live_gatt_callback_records_service_inventory(self):
        self.assertIn("void onServicesDiscovered(BluetoothGatt link,int code)", BLE)
        callback = BLE.split("void onServicesDiscovered(", 1)[1].split("    @Override", 1)[0]
        self.assertIn("if(link!=gatt)return;", callback)
        self.assertIn("if(code!=BluetoothGatt.GATT_SUCCESS)", callback)
        self.assertIn("for(BluetoothGattService discoveredService:link.getServices())", callback)
        self.assertIn("GattDiscoverySnapshot.observed(", callback)
        self.assertLess(callback.index("if(code!=BluetoothGatt.GATT_SUCCESS)"),
                        callback.index("GattDiscoverySnapshot.observed("))
        self.assertIn("lastGattDiscovery=GattDiscoverySnapshot.failed();", callback)

    def test_snapshot_cleared_before_new_connection_but_visible_after_disconnect(self):
        self.assertIn("lastGattDiscovery=GattDiscoverySnapshot.notChecked();", BLE)
        self.assertIn("lastGattDiscoveryReport(){return lastGattDiscovery.report();}", BLE)
        disconnect = BLE.split("public synchronized void disconnect()", 1)[1].split(
            "public int maxFrameBytes()", 1)[0]
        self.assertNotIn("lastGattDiscovery=", disconnect)

    def test_phone_report_includes_redacted_discovery_evidence(self):
        self.assertIn('"Last GATT discovery: "+ble.lastGattDiscoveryReport()', MAIN)
        self.assertIn('advancedButton("Copy BLE hardware test report"', MAIN)
        self.assertIn('advancedButton("Check AI BLE channel"', MAIN)
        self.assertNotIn("getAddress()", SNAPSHOT)
        self.assertNotIn("getName()", SNAPSHOT)
        self.assertIn("handshake UNVERIFIED", SNAPSHOT)
        self.assertIn("Android GATT cache or firmware service registration", SNAPSHOT)


if __name__ == "__main__":
    unittest.main()
