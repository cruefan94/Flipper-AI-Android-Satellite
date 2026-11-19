"""Regression checks for active GATT device identity during explicit pairing.

These are source invariants, not Android hardware/Binder tests.
"""
from pathlib import Path
import re
import unittest

SOURCE = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java").read_text(encoding="utf-8")


class PairSessionIdentityTests(unittest.TestCase):
    def test_pair_is_serialized_with_gatt_callbacks(self):
        self.assertIn("public synchronized void pair(BluetoothDevice device)", SOURCE)

    def test_rejects_other_device_before_mutating_last_device(self):
        match = re.search(r"public synchronized void pair\(BluetoothDevice device\)\{(.*?)\n  public void pair\(\)", SOURCE, re.S)
        self.assertIsNotNone(match)
        body = match.group(1)
        guard = body.index("if(gatt!=null && device!=lastDevice)")
        assignment = body.index("lastDevice=device;")
        self.assertLess(guard, assignment)
        self.assertIn("return;", body[guard:assignment])

    def test_explicit_connect_still_invalidates_recovery(self):
        match = re.search(r"public synchronized void connect\(BluetoothDevice device\)\{(.*?)\n  \}", SOURCE, re.S)
        self.assertIsNotNone(match)
        self.assertIn("recoveryToken++;", match.group(1))
        self.assertIn("discoveryEvidence.clear();", match.group(1))


if __name__ == "__main__":
    unittest.main()
