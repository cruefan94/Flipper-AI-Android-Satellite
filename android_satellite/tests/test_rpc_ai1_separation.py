"""Static regressions: stock Momentum RPC must never imply custom AI1 readiness.

These inspect source invariants only, not physical BLE interoperability.
"""
from pathlib import Path
import re
import unittest

SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/flipperai/satellite/BleSatellite.java")


class RpcAi1SeparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def section(self, start, end):
        self.assertIn(start, self.source)
        self.assertIn(end, self.source)
        return self.source.split(start, 1)[1].split(end, 1)[0]

    def test_stock_rpc_is_not_ai_ready(self):
        body = self.section("boolean isReady(){", "@Override public boolean sendFrame")
        self.assertIn("subscribed && approved()", body)
        self.assertIn("TransportState.Phase.READY", body)
        self.assertNotIn("stockRpcSubscribed", body)

    def test_ai_send_fails_closed_without_ai_ready(self):
        body = self.section("boolean send(String frame){", "private synchronized boolean drain()")
        self.assertRegex(body, r"!isReady\\(\\)")
        self.assertIn("return false;", body)

    def test_rpc_discovery_explicitly_reports_ai_not_ready(self):
        body = self.section("void onServicesDiscovered(BluetoothGatt link,int code){",
                            "@Override public void onDescriptorWrite")
        self.assertIn("if(service==null)", body)
        self.assertIn("stockRpcDetected=true", body)
        self.assertIn("Stock Momentum RPC", body)
        self.assertNotIn("subscribed=true", body)

    def test_rpc_subscription_does_not_promote_ai_state(self):
        body = self.section("void onDescriptorWrite(BluetoothGatt link,BluetoothGattDescriptor d,int code){",
                            "@Override public void onMtuChanged")
        self.assertIn("stockRpcSubscribed=code==BluetoothGatt.GATT_SUCCESS", body)
        self.assertIn("AI not ready", body)
        self.assertNotIn("subscribed=true", body)

    def test_ai_notifications_require_current_link_and_ready_state(self):
        body = self.section("void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch,byte[] value){",
                            "@Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic ch){")
        self.assertIn("if(link!=gatt || ch==null)return;", body)
        self.assertIn("TX.equals(ch.getUuid()) && isReady()", body)


if __name__ == "__main__":
    unittest.main()
