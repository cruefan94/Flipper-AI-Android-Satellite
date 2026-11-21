"""Source-level regression for delayed GATT 147 retry invalidation.

This supplements, but does not replace, device/Android callback-order tests.
"""
from pathlib import Path
import re
import unittest

SOURCE = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java")


class Gatt147RetryTokenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_delayed_147_retry_captures_recovery_token(self):
        branch = self.source.split("if(code==147 && !retry147Used && approved())", 1)[1]
        branch = branch.split("}else{", 1)[0]
        self.assertIn("final int retryToken=recoveryToken;", branch)
        self.assertRegex(branch, r"retryToken\s*!=\s*recoveryToken")
        self.assertIn("generation!=connectionGeneration", branch)
        self.assertIn("retryDevice!=lastDevice", branch)
        self.assertIn("userDisconnected", branch)

    def test_manual_disconnect_invalidates_delayed_retry(self):
        manual = self.source.split("void manualDisconnect()", 1)[1].split("private synchronized void scheduleRecovery", 1)[0]
        self.assertRegex(manual, r"recoveryToken\s*\+\+")
        self.assertIn("userDisconnected=true;", manual)

    def test_explicit_connect_invalidates_delayed_retry(self):
        connect = self.source.split("void connect(BluetoothDevice device)", 1)[1].split("void manualDisconnect()", 1)[0]
        self.assertRegex(connect, r"recoveryToken\s*\+\+")
        self.assertIn("if(device==null)", connect)


if __name__ == "__main__":
    unittest.main()
