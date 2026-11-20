"""Fail-closed source regression for user-initiated BLE disconnect and retry cancellation.

These checks are not Android runtime or physical-radio tests.
"""
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java"


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"Missing method: {signature}")
    brace = source.find("{", start + len(signature))
    if brace < 0:
        raise AssertionError(f"Missing body: {signature}")
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:i]
    raise AssertionError(f"Unterminated method: {signature}")


class ManualDisconnectRecoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_explicit_stop_sets_flag_and_invalidates_recovery_before_disconnect(self):
        body = method_body(self.source, "public synchronized void manualDisconnect()")
        positions = [body.find(term) for term in (
            "userDisconnected=true", "recoveryToken++", "recoveryAttempts=0", "disconnect();"
        )]
        self.assertTrue(all(p >= 0 for p in positions), positions)
        self.assertEqual(positions, sorted(positions))

    def test_delayed_recovery_rechecks_stop_token_and_device(self):
        body = method_body(self.source, "private synchronized void scheduleRecovery(")
        self.assertIn("if(userDisconnected || failedDevice==null || failedDevice!=lastDevice)return;", body)
        self.assertRegex(body, r"final int token=\+\+recoveryToken;")
        self.assertIn("if(userDisconnected || token!=recoveryToken || failedDevice!=lastDevice)return;", body)

    def test_manual_stop_does_not_trigger_automatic_reconnect(self):
        body = method_body(self.source, "public synchronized void manualDisconnect()")
        self.assertNotIn("openConnection(", body)
        self.assertNotIn("scheduleRecovery(", body)

    def test_stale_callback_is_rejected_before_connection_transition(self):
        body = method_body(self.source, "onConnectionStateChange(BluetoothGatt link,int code,int status)")
        self.assertIn("if(link!=gatt)return;", body)
        self.assertLess(body.index("if(link!=gatt)return;"), body.index("transitionConnected()"))


if __name__ == "__main__":
    unittest.main()
