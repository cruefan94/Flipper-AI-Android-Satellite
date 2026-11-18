"""Public-safe Android AI1-to-Momentum wire contract regression checks.

These tests pin the reviewed GATT/wire constants from the private firmware,
without copying private firmware source or any secret into this public repo.
They are static checks, not evidence of a real Flipper/phone BLE connection.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/flipperai/satellite"


def read(name):
    return (SOURCE / name).read_text(encoding="utf-8")


class MomentumAi1ContractTest(unittest.TestCase):
    def test_gatt_uuid_roles_and_rpc_remain_distinct(self):
        ble = read("BleSatellite.java")
        for constant, uuid in {
            "SERVICE": "c8c7ab10-6355-4ea8-a76a-0a4a64de0001",
            "RX": "c8c7ab10-6355-4ea8-a76a-0a4a64de0002",
            "TX": "c8c7ab10-6355-4ea8-a76a-0a4a64de0003",
        }.items():
            self.assertIn(
                f'UUID {constant}=UUID.fromString("{uuid}")', ble,
                f"{constant} UUID drifted from Momentum AI1 service",
            )
        self.assertIn("MOMENTUM_RPC_SERVICE", ble)
        self.assertIn("if(service==null)", ble)
        self.assertIn("Stock Momentum RPC indications enabled", ble)
        self.assertIn("AI not ready", ble)

    def test_mtu_single_frame_and_serialized_gatt_writes(self):
        ble = read("BleSatellite.java")
        state = read("TransportState.java")
        proto = read("SatelliteProtocol.java")
        self.assertIn("MAX_BYTES = 180", proto)
        self.assertIn("MIN_AI_ATT_PAYLOAD=32", state)
        self.assertIn("bytes<=mtu-3", state)
        self.assertIn("link.requestMtu(185)", ble)
        self.assertIn("writeGuard.isOutstanding()", ble)
        self.assertIn("onCharacteristicWrite(", ble)
        self.assertIn("onDescriptorWrite(", ble)
        self.assertIn("BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE", ble)
        self.assertIn("MAX_QUEUED=8", ble)

    def test_frame_versions_requests_and_reconnect_boundaries(self):
        proto = read("SatelliteProtocol.java")
        session = read("ProtocolSession.java")
        handler = read("SatelliteRequestHandler.java")
        ai = read("BleAiRequestProcessor.java")
        self.assertIn(r'AI1|%s|%d|%s\\n', proto)
        self.assertIn('"flipper-v1"', session)
        self.assertIn('"HELLO_ACK"', handler)
        self.assertIn('"android-v1"', handler)
        self.assertIn('"ASK"', handler)
        self.assertIn('"ANSWER"', handler)
        self.assertIn('"REPLAY_ID"', session)
        self.assertIn("linkEpoch.matches(epoch)", ai)
        self.assertIn("jobs.cancelAll()", ai)
        self.assertIn("cloudConsent=false", ai)


if __name__ == "__main__":
    unittest.main()
