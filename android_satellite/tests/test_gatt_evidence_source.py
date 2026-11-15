#!/usr/bin/env python3
"""Android BLE GATT evidence must be current-link bound, privacy safe and read-only."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
BLE=(SRC/"BleSatellite.java").read_text()
UI=(SRC/"MainActivity.java").read_text()
EVIDENCE=(SRC/"GattDiscoveryEvidence.java").read_text()

class GattEvidenceSourceTest(unittest.TestCase):
    def test_stale_callbacks_cannot_update_discovery(self):
        callback=BLE.split("void onServicesDiscovered(",1)[1].split(
            "    @Override",1)[0]
        self.assertLess(callback.index("if(link!=gatt)return;"),
                        callback.index("discoveryEvidence.failed(code)"))
        self.assertLess(callback.index("if(link!=gatt)return;"),
                        callback.index("discoveryEvidence.succeeded("))
        self.assertIn("if(!approved()){disconnect();return;}",callback)
        self.assertIn("if(code!=BluetoothGatt.GATT_SUCCESS){",callback)

    def test_manual_new_device_clears_previous_evidence_but_retry_preserves_history(self):
        self.assertIn("public synchronized void connect(BluetoothDevice device){\n    // A new explicitly selected connection must not reuse another device's evidence.\n    discoveryEvidence.clear();",BLE)
        self.assertIn("discoveryEvidence.beginAttempt();",BLE)
        self.assertIn("discoveryEvidence.closeLink();",BLE)
        self.assertIn("LAST RESULT (link dropped/reconnecting)",EVIDENCE)

    def test_privacy_safe_read_only_report(self):
        self.assertIn("gattDiscoveryReport()",BLE)
        self.assertIn("gattDiscoveryNextStep()",BLE)
        self.assertIn('advancedButton("Copy BLE hardware test report"',UI)
        self.assertIn('advancedButton("Explain last BLE discovery"',UI)
        self.assertIn("ble.gattDiscoveryReport()",UI)
        self.assertIn("ble.gattDiscoveryNextStep()",UI)
        for forbidden in ("getAddress(", "getBondedDevices(", "X-Device-Token",
                          "shareToServer(", "writeCharacteristic(", "createBond("):
            self.assertNotIn(forbidden,EVIDENCE)

    def test_does_not_claim_rpc_is_proof_of_failed_firmware_registration(self):
        self.assertIn("firmware registration not independently confirmed",UI)
        self.assertIn("cached phone discovery is not proof",EVIDENCE)
        self.assertIn("Do not automatically unpair",EVIDENCE)
        self.assertIn("Discovery alone does not prove AI chat works",EVIDENCE)
        self.assertIn("Service presence not established",EVIDENCE)

if __name__=="__main__":unittest.main()
