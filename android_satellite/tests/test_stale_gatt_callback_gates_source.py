#!/usr/bin/env python3
"""Regression guard: delayed Android GATT callbacks cannot affect a replacement link.

These are source-structure checks, not hardware or Binder scheduling tests.
"""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/flipperai/satellite/BleSatellite.java").read_text(encoding="utf-8")


def callback_body(method, next_method):
    callback = SOURCE.split("private final BluetoothGattCallback callback=", 1)[1]
    return callback.split(method, 1)[1].split(next_method, 1)[0]


class StaleGattCallbackGatesTest(unittest.TestCase):
    def test_disconnect_invalidates_link_before_closing_old_handle(self):
        body = SOURCE.split("public synchronized void disconnect()", 1)[1].split(
            "public String gattDiscoveryReport()", 1)[0]
        self.assertIn("connectionGeneration++;", body)
        self.assertIn("BluetoothGatt old=gatt;gatt=null;", body)
        self.assertLess(body.index("gatt=null;"), body.index("old.disconnect();"))

    def test_connection_and_discovery_callbacks_reject_old_handle(self):
        connection = callback_body("void onConnectionStateChange(", "void onServicesDiscovered(")
        discovery = callback_body("void onServicesDiscovered(", "void onDescriptorWrite(")
        self.assertIn("synchronized(BleSatellite.this)", connection)
        self.assertIn("if(link!=gatt)return;", connection)
        self.assertIn("synchronized(BleSatellite.this)", discovery)
        self.assertIn("if(link!=gatt)return;", discovery)

    def test_descriptor_callbacks_reject_old_handle_and_wrong_cccd(self):
        descriptor = callback_body("void onDescriptorWrite(", "void onCharacteristicChanged(")
        self.assertIn("synchronized(BleSatellite.this)", descriptor)
        self.assertIn("link!=gatt", descriptor)
        self.assertIn("!CCCD.equals(d.getUuid())", descriptor)

    def test_setup_timeout_is_scoped_to_original_link(self):
        timeout = SOURCE.split("private synchronized void expireSetup(", 1)[1].split(
            "public synchronized void reconnect()", 1)[0]
        self.assertIn("link!=gatt", timeout)

    def test_147_delayed_retry_checks_connection_generation(self):
        connection = callback_body("void onConnectionStateChange(", "void onServicesDiscovered(")
        self.assertIn("final int generation=connectionGeneration;", connection)
        self.assertIn("generation!=connectionGeneration", connection)
        self.assertIn("userDisconnected", connection)
        self.assertIn("retryDevice!=lastDevice", connection)


if __name__ == "__main__":
    unittest.main()
