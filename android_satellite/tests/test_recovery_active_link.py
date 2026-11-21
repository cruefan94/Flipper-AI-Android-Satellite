"""Regression checks for delayed BLE recovery vs an active GATT handle.

Source invariants only; physical Android callback-order testing remains required.
"""
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java"


class RecoveryActiveLinkTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.recovery = cls.source.split("private synchronized void scheduleRecovery(", 1)[1].split(
            "private synchronized void openConnection(", 1
        )[0]

    def test_delayed_retry_rejects_active_gatt(self):
        self.assertRegex(self.recovery, r"token\s*!=\s*recoveryToken[^;]*gatt\s*!=\s*null")

    def test_delayed_retry_checks_manual_stop_and_selected_device(self):
        self.assertIn("userDisconnected", self.recovery)
        self.assertIn("failedDevice!=lastDevice", self.recovery)

    def test_delayed_retry_still_reconnects_after_guard(self):
        self.assertRegex(self.recovery, r"gatt\s*!=\s*null\)return;[\s\S]*openConnection\(failedDevice\)")

    def test_explicit_connect_invalidates_old_retry(self):
        connect = self.source.split("void connect(BluetoothDevice device)", 1)[1].split(
            "void manualDisconnect()", 1
        )[0]
        self.assertIn("recoveryToken++;", connect)


if __name__ == "__main__":
    unittest.main()
