#!/usr/bin/env python3
"""Source regression: a null connectGatt result must not strand an opted-in link."""
from pathlib import Path
import unittest

SOURCE=(Path(__file__).resolve().parents[1] /
        "app/src/main/java/com/flipperai/satellite/BleSatellite.java").read_text(encoding="utf-8")

class NullGattRecoveryTest(unittest.TestCase):
    def test_null_handle_uses_cancelable_backoff(self):
        start=SOURCE.index("BluetoothGatt link=device.connectGatt(")
        end=SOURCE.index("gatt=link;",start)
        block=SOURCE[start:end]
        self.assertIn("if(link==null)",block)
        self.assertIn("disconnect();",block)
        self.assertIn("scheduleRecovery(device,-9);",block)
        self.assertLess(block.index("disconnect();"),block.index("scheduleRecovery(device,-9);"))
        self.assertNotIn("scheduleRecovery(device,-9);",SOURCE[end:])

    def test_recovery_respects_manual_disconnect_and_selected_device(self):
        recovery=SOURCE.split("private synchronized void scheduleRecovery(",1)[1].split("private synchronized void openConnection(",1)[0]
        self.assertIn("userDisconnected",recovery)
        self.assertIn("token!=recoveryToken",recovery)
        self.assertIn("failedDevice!=lastDevice",recovery)
        manual=SOURCE.split("public synchronized void manualDisconnect()",1)[1].split("private synchronized void scheduleRecovery(",1)[0]
        self.assertIn("userDisconnected=true;",manual)
        self.assertIn("recoveryToken++;",manual)

if __name__=="__main__":
    unittest.main()
