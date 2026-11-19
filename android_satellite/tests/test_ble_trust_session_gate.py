"""Regression gates for Android BLE trust and session ownership.

These source guards are deliberately narrower than device-level acceptance:
they do not prove foreground-service ownership or an AI1 handshake.
Run: python3 -m unittest discover -s android_satellite/tests -p 'test_ble_trust_session_gate.py'
"""
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java"


def method(name):
    source = SOURCE.read_text(encoding="utf-8")
    match = re.search(r"\b(?:void|boolean|BluetoothDevice)\s+" + re.escape(name) + r"\s*\([^)]*\)\s*\{", source)
    if not match:
        raise AssertionError("Missing BLE method: " + name)
    start = match.end()
    depth = 1
    for index in range(start, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index]
    raise AssertionError("Unterminated BLE method: " + name)


class BleTrustSessionGate(unittest.TestCase):
    def test_pair_rejects_device_swap_during_active_gatt(self):
        body = method("pair")
        self.assertIn("gatt!=null && device!=lastDevice", body)
        self.assertLess(body.index("gatt!=null && device!=lastDevice"), body.index("lastDevice=device"))

    def test_disconnect_invalidates_session_and_pending_work(self):
        body = method("disconnect")
        for required in ("connectionGeneration++", "gatt=null", "pending.clear()", "writeGuard.reset()"):
            self.assertIn(required, body)

    def test_manual_disconnect_cancels_recovery(self):
        body = method("manualDisconnect")
        for required in ("userDisconnected=true", "recoveryToken++", "disconnect()"):
            self.assertIn(required, body)

    def test_confirmed_device_requires_real_link_readiness(self):
        body = method("confirmedBondedDevice")
        self.assertIn("gatt==null", body)
        self.assertIn("approved()", body)
        self.assertIn("isReady() || isStockRpcSubscribed()", body)

    def test_stock_rpc_does_not_imply_custom_ai(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("Standard Flipper Bluetooth RPC is not compatible", source)
        self.assertNotEqual(re.search(r"MOMENTUM_RPC_SERVICE=UUID.fromString\(\"([^\"]+)", source).group(1),
                            re.search(r"\bSERVICE=UUID.fromString\(\"([^\"]+)", source).group(1))


if __name__ == "__main__":
    unittest.main()
