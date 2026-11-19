"""Structural regression for Android BLE notification callback session isolation.

These assertions are source checks, not a Binder concurrency or hardware test.
"""
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java"


class NotificationCallbackIsolationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def callback_body(self, signature, next_anchor):
        start = self.source.index(signature)
        end = self.source.index(next_anchor, start)
        return self.source[start:end]

    def test_api33_callback_is_serialized_and_filters_stale_link(self):
        body = self.callback_body(
            "void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch,byte[] value)",
            "@Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch)")
        self.assertIn("synchronized(BleSatellite.this)", body)
        self.assertIn("if(link!=gatt || ch==null)return;", body)
        self.assertLess(body.index("if(link!=gatt || ch==null)return;"),
                        body.index("rpcNotificationsReceived++;"))
        self.assertIn("TX.equals(ch.getUuid()) && isReady()", body)

    def test_legacy_callback_is_serialized_and_filters_stale_link(self):
        body = self.source.split(
            "void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch){", 1)[1]
        self.assertIn("if(Build.VERSION.SDK_INT>=33)return;", body)
        self.assertIn("synchronized(BleSatellite.this)", body)
        self.assertIn("if(link!=gatt || ch==null)return;", body)
        self.assertLess(body.index("if(link!=gatt || ch==null)return;"),
                        body.index("rpcNotificationsReceived++;"))
        self.assertIn("TX.equals(ch.getUuid()) && isReady()", body)


if __name__ == "__main__":
    unittest.main()
