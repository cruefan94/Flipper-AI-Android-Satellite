"""Source-level guard against stale Android GATT callbacks.

This is a structural regression, NOT a Binder scheduling or hardware test.
"""
from pathlib import Path
import re
import unittest

SOURCE = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java")


def callback_bodies(source, name):
    pattern = re.compile(r"@Override\\s+public\\s+void\\s+" + re.escape(name) + r"\\s*\\(")
    results = []
    for match in pattern.finditer(source):
        start = source.find("{", match.end())
        if start < 0:
            continue
        depth = 0
        for pos in range(start, len(source)):
            if source[pos] == "{":
                depth += 1
            elif source[pos] == "}":
                depth -= 1
                if depth == 0:
                    results.append(source[start + 1:pos])
                    break
    return results


class GattCallbackSessionAudit(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_all_gatt_callbacks_use_session_monitor(self):
        for name in ("onConnectionStateChange", "onServicesDiscovered",
                     "onDescriptorWrite", "onMtuChanged",
                     "onCharacteristicRead", "onCharacteristicWrite",
                     "onCharacteristicChanged"):
            with self.subTest(callback=name):
                bodies = callback_bodies(self.source, name)
                self.assertTrue(bodies, "missing callback: " + name)
                for body in bodies:
                    self.assertIn("synchronized(BleSatellite.this)", body)

    def test_all_gatt_callbacks_reject_obsolete_handle(self):
        for name in ("onConnectionStateChange", "onServicesDiscovered",
                     "onDescriptorWrite", "onMtuChanged",
                     "onCharacteristicRead", "onCharacteristicWrite",
                     "onCharacteristicChanged"):
            with self.subTest(callback=name):
                for body in callback_bodies(self.source, name):
                    self.assertRegex(body, r"link\\s*(?:!=|==)\\s*gatt")

    def test_both_notification_overloads_reject_null_characteristics(self):
        bodies = callback_bodies(self.source, "onCharacteristicChanged")
        self.assertEqual(2, len(bodies))
        for body in bodies:
            self.assertRegex(body, r"link\\s*!=\\s*gatt\\s*\\|\\|\\s*ch\\s*==\\s*null")

    def test_disconnect_invalidates_handle_before_closing(self):
        match = re.search(r"public synchronized void disconnect\\s*\\(\\s*\\)", self.source)
        self.assertIsNotNone(match)
        body = self.source[match.end():self.source.find("public String gattDiscoveryReport", match.end())]
        self.assertIn("connectionGeneration++", body)
        self.assertIn("gatt=null", body)
        self.assertLess(body.index("gatt=null"), body.index("old.disconnect()"))

    def test_delayed_setup_and_write_timeouts_match_active_handle(self):
        self.assertIn("if(link!=gatt || isReady()", self.source)
        self.assertIn("if(link!=gatt || !writeGuard.isPending(token))return;", self.source)


if __name__ == "__main__":
    unittest.main()
