#!/usr/bin/env python3
"""Source audit: a disconnected Flipper cannot leave queued network inference running."""
from pathlib import Path
import unittest

SRC=Path(__file__).resolve().parents[1]/"app/src/main/java/com/flipperai/satellite"
UI=(SRC/"MainActivity.java").read_text()
JOBS=(SRC/"BleSessionJobs.java").read_text()
CLOUD=(SRC/"WorkerCloudEngine.java").read_text()
LOCAL=(SRC/"LocalModelEngine.java").read_text()

class BleRequestCancellationWiringTest(unittest.TestCase):
    def test_ble_dispatch_only_through_cancelable_gate(self):
        self.assertIn("new BleSessionJobs(inferenceWorker)",UI)
        block=UI.split("private void processReceived(",1)[1].split("@Override public void device(",1)[0]
        self.assertIn("bleSessionJobs.submit(()->{",block)
        self.assertIn("connectionEpoch.matches(epoch)",block)
        self.assertIn("consentEpoch.current(consentGeneration)",block)
        self.assertIn("transports.sendFrameIfCurrent(response,transportGeneration)",block)
        self.assertNotIn("inferenceWorker.execute(",block)

    def test_disconnect_invalidates_all_sources_before_session_reuse(self):
        block=UI.split('if(text.startsWith("Disconnected") || text.startsWith("Connecting")){',1)[1]
        block=block.split("showStatus(text);",1)[0]
        for expression in ("transports.invalidateSession();","connectionEpoch.advance();",
                           "bleSessionJobs.cancelAll();","cloud.revokeActive();",
                           "localModel.revokeActive();"):
            self.assertIn(expression,block)
        self.assertLess(block.index("bleSessionJobs.cancelAll();"),block.index("inferenceWorker.execute(session::reset);"))

    def test_destroy_closes_gate_before_worker_shutdown(self):
        block=UI.split("@Override public void onDestroy()",1)[1]
        self.assertLess(block.index("bleSessionJobs.close();"),block.index("inferenceWorker.shutdown();"))
        self.assertIn("pending.remove(this);",JOBS)
        self.assertIn("task.cancel(true)",JOBS)
        self.assertIn("if(closed)return false",JOBS)

    def test_cloud_and_loopback_can_abort_inflight_requests(self):
        self.assertIn("public synchronized void revokeActive()",CLOUD)
        self.assertIn("public synchronized void revokeActive()",LOCAL)

if __name__=="__main__":
    unittest.main()
