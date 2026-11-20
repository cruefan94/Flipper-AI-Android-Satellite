"""Source-level safeguards for Android BLE service ownership.

These tests do not simulate Android GATT or prove hardware reconnect.
"""
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleConnectionService.java"


class BleServiceLifecycleSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_service_owns_transport_and_inference_worker(self):
        self.assertIn("extends Service implements BleSatellite.Events", self.source)
        self.assertIn("transport=new BleSatellite(this,this);", self.source)
        self.assertIn("new BleAiRequestProcessor(", self.source)
        self.assertIn("inferenceWorker", self.source)

    def test_explicit_disconnect_invalidates_link_and_stops_service(self):
        body = self.source.split("public void stopFromUser(){", 1)[1].split("\n  }", 1)[0]
        for required in ("linkHealth.userDisconnect(", "transport.manualDisconnect()", "stopSelf()"):
            self.assertIn(required, body)
        self.assertLess(body.index("transport.manualDisconnect()"), body.index("stopSelf()"))

    def test_background_service_does_not_resurrect_after_process_death(self):
        self.assertIn("return START_NOT_STICKY;", self.source)
        self.assertIn("ACTION_KEEP.equals(action)", self.source)
        self.assertIn("startForeground(NOTICE_ID,notice())", self.source)
        self.assertIn("catch(SecurityException | IllegalStateException denied)", self.source)

    def test_unattached_activity_does_not_receive_stale_ai_frames(self):
        body = self.source.split("public void received(String frame){", 1)[1].split("\n  }", 1)[0]
        self.assertIn("transport.isReady()", body)
        self.assertIn("aiRequests.receive(frame)", body)
        self.assertNotIn("currentListener.received(", body)

    def test_manual_stop_revokes_ai_requests_before_gatt_disconnect(self):
        body = self.source.split("public void stopFromUser(){", 1)[1].split("\n  }", 1)[0]
        self.assertIn("aiRequests.disconnected()", body)
        self.assertLess(body.index("aiRequests.disconnected()"), body.index("transport.manualDisconnect()"))
        self.assertLess(body.index("transport.manualDisconnect()"), body.index("stopSelf()"))

    def test_destroy_cancels_worker_and_closes_gatt(self):
        body = self.source.split("public void onDestroy(){", 1)[1].split("super.onDestroy();", 1)[0]
        for required in ("aiRequests.close()", "transport.close()", "inferenceWorker.purge()", "inferenceWorker.shutdown()"):
            self.assertIn(required, body)


if __name__ == "__main__":
    unittest.main()
