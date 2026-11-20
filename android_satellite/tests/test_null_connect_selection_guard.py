"""Fail-closed regression for empty Bluetooth connect selections.

Source-level guard only: this does not establish hardware reconnect behavior.
"""
from pathlib import Path
import re
import unittest

SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/flipperai/satellite/BleSatellite.java")


class NullConnectSelectionGuardTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        match = re.search(
            r"public synchronized void connect\(BluetoothDevice device\)\s*\{"
            r"(?P<body>.*?)\n  \}",
            cls.source, re.S)
        assert match is not None, "synchronized connect method missing"
        cls.body = match.group("body")

    def test_null_selection_returns_before_mutating_session(self):
        guard = re.search(
            r"if\s*\(device\s*==\s*null\)\s*\{"
            r"\s*events\.status\([^;]+;\s*return;\s*\}",
            self.body)
        self.assertIsNotNone(guard, "null selection must return early")
        for operation in ("discoveryEvidence.clear()", "recoveryToken++",
                          "userDisconnected=false", "openConnection(device)"):
            self.assertIn(operation, self.body)
            self.assertLess(guard.end(), self.body.index(operation),
                            f"{operation} must happen only after null guard")

    def test_guard_does_not_trigger_disconnect(self):
        guard = re.search(r"if\s*\(device\s*==\s*null\)(.*?)\}", self.body, re.S)
        self.assertIsNotNone(guard)
        self.assertNotIn("disconnect(", guard.group(1))
        self.assertNotIn("openConnection(", guard.group(1))


if __name__ == "__main__":
    unittest.main()
