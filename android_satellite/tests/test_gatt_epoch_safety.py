"""Static BLE reconnect/epoch safety regressions.

These tests protect source invariants; physical Android GATT behavior is not proven.
"""
from pathlib import Path
import re
import unittest

SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/flipperai/satellite/BleSatellite.java")


class GattEpochSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def body(self, start, end):
        self.assertIn(start, self.source)
        return self.source.split(start, 1)[1].split(end, 1)[0]

    def test_manual_stop_invalidates_queued_reconnect(self):
        body = self.body("void manualDisconnect(){", "private synchronized void scheduleRecovery")
        self.assertRegex(body, r"userDisconnected\s*=\s*true")
        self.assertRegex(body, r"recoveryToken\s*\+\+")
        self.assertIn("disconnect();", body)

    def test_recovery_callback_rechecks_session_and_selected_device(self):
        body = self.body("void scheduleRecovery(BluetoothDevice failedDevice,int gattStatus){",
                         "private synchronized void openConnection")
        self.assertRegex(body, r"final int token\s*=\s*\+\+recoveryToken")
        self.assertIn("token!=recoveryToken", body)
        self.assertIn("failedDevice!=lastDevice", body)
        self.assertIn("userDisconnected", body)
        self.assertLess(body.index("token!=recoveryToken"), body.rindex("openConnection(failedDevice)"))

    def test_gatt_147_delayed_retry_rechecks_generation(self):
        body = self.body("if(code==147 && !retry147Used && approved()){",
                         "}else{")
        self.assertIn("final int generation=connectionGeneration", body)
        self.assertIn("generation!=connectionGeneration", body)
        self.assertIn("retryDevice!=lastDevice", body)
        self.assertIn("userDisconnected", body)
        self.assertLess(body.index("generation!=connectionGeneration"),
                        body.rindex("openConnection(retryDevice)"))

    def test_stale_gatt_callbacks_cannot_mutate_new_link(self):
        body = self.body("void onConnectionStateChange(BluetoothGatt link,int code,int status){",
                         "@Override public void onServicesDiscovered")
        self.assertIn("if(link!=gatt)return;", body)
        self.assertLess(body.index("if(link!=gatt)return;"), body.index("transitionConnected()"))

    def test_null_selection_does_not_invalidate_current_session(self):
        body = self.body("void connect(BluetoothDevice device){",
                         "public synchronized void manualDisconnect")
        self.assertIn("if(device==null)", body)
        self.assertLess(body.index("if(device==null)"), body.index("recoveryToken++"))


if __name__ == "__main__":
    unittest.main()
