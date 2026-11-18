#!/usr/bin/env python3
"""Static regression guards for the opt-in selected-device BLE recovery path."""
from pathlib import Path
import unittest

SRC = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite"
BLE = (SRC / "BleSatellite.java").read_text()
UI = (SRC / "MainActivity.java").read_text()

class ReconnectSourceTests(unittest.TestCase):
    def test_bounded_retry_and_manual_stop(self):
        self.assertIn("RECOVERY_DELAYS_MS={", BLE)
        self.assertIn("60000L", BLE)
        self.assertIn("recoveryAttempts=Math.min(recoveryAttempts+1,RECOVERY_DELAYS_MS.length)", BLE)
        self.assertIn("RECOVERY_DELAYS_MS[stage-1]", BLE)
        self.assertIn("backoff ", BLE)
        self.assertNotIn("recoveryAttempts>=RECOVERY_DELAYS_MS.length", BLE)
        self.assertIn("if(userDisconnected || token!=recoveryToken || failedDevice!=lastDevice)return;", BLE)
        self.assertIn("public synchronized void manualDisconnect()", BLE)
        self.assertIn("userDisconnected=true;", BLE)
        self.assertIn("recoveryToken++;", BLE)

    def test_recovery_after_disconnect_and_setup_timeout(self):
        self.assertIn("scheduleRecovery(failedDevice,code);", BLE)
        self.assertIn("scheduleRecovery(timedOutDevice,-1);", BLE)
        self.assertIn("scheduleRecovery(failedDevice,-2);", BLE)
        self.assertIn("if(link!=gatt || !writeGuard.isPending(token))return;", BLE)
        self.assertIn("if(link!=gatt || isReady() || (stockRpcDetected && stockRpcSubscribed))return;", BLE)
        self.assertIn("if(link!=gatt)return;", BLE)

    def test_manual_actions_cancel_recovery(self):
        self.assertIn('if(bleService!=null)bleService.stopFromUser();',UI)
        self.assertIn('public synchronized void manualDisconnect()',BLE)
        self.assertIn('if(userDisconnected || generation!=connectionGeneration',BLE)
        self.assertIn('BLUETOOTH: RECONNECTING... (AI NOT READY)', UI)

    def test_duplicate_connected_event_is_idempotent(self):
        self.assertIn("if(state.alreadyConnected())", BLE)
        self.assertIn("Duplicate BLE connected callback ignored", BLE)
        self.assertIn("if(link!=gatt)return;", BLE)

    def test_stock_rpc_not_ai_ready(self):
        self.assertIn("stockRpcSubscribed", BLE)
        self.assertIn("AI not ready", BLE)
        self.assertIn("public boolean isStockRpcSubscribed()", BLE)
        self.assertIn("public synchronized boolean send(String frame)", BLE)

if __name__ == "__main__":
    unittest.main()
