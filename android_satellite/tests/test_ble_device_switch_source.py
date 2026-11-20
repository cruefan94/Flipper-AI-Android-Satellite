"""Static regression: explicit Flipper switching invalidates prior AI1 requests.

These assertions do not establish physical BLE behavior.
"""
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleConnectionService.java"

class DeviceSwitchSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_user_switch_invalidates_ai1_before_connect(self):
        body = self.source.split("public void connectFromUser(BluetoothDevice chosen) {", 1)[1].split("\n  }", 1)[0]
        self.assertIn("if(chosen==null || transport==null)return;", body)
        self.assertIn("aiRequests.disconnected()", body)
        self.assertLess(body.index("aiRequests.disconnected()"), body.index("transport.connect(chosen)"))

    def test_switch_uses_service_owned_transport(self):
        body = self.source.split("public void connectFromUser(BluetoothDevice chosen) {", 1)[1].split("\n  }", 1)[0]
        self.assertIn("transport.connect(chosen)", body)
        self.assertNotIn("new BleSatellite(", body)

if __name__ == "__main__":
    unittest.main()
