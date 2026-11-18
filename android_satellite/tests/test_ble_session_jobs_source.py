#!/usr/bin/env python3
"""Source audit: service-owned, connection-scoped AI1 inference cancellation."""
from pathlib import Path
import unittest

SRC=Path(__file__).resolve().parents[1]/"app/src/main/java/com/flipperai/satellite"
SERVICE=(SRC/"BleConnectionService.java").read_text(encoding="utf-8")
MAIN=(SRC/"MainActivity.java").read_text(encoding="utf-8")
PROCESSOR=(SRC/"BleAiRequestProcessor.java").read_text(encoding="utf-8")
JOBS=(SRC/"BleSessionJobs.java").read_text(encoding="utf-8")
CLOUD=(SRC/"WorkerCloudEngine.java").read_text(encoding="utf-8")
LOCAL=(SRC/"LocalModelEngine.java").read_text(encoding="utf-8")


class BleRequestCancellationWiringTest(unittest.TestCase):
    def test_service_is_single_ai1_protocol_owner(self):
        self.assertIn("new BleAiRequestProcessor(",SERVICE)
        self.assertIn("new SatelliteRequestHandler(router)",SERVICE)
        self.assertIn("aiRequests.receive(frame)",SERVICE)
        self.assertNotIn("listener.received(frame)",SERVICE)
        self.assertNotIn("new SatelliteRequestHandler(",MAIN)
        self.assertNotIn("processReceived(",MAIN)
        self.assertIn("leaveAiSettingsScreen()",MAIN)
        self.assertIn("setVisibleAiPolicy(",MAIN)

    def test_ble_dispatch_only_through_cancelable_worker(self):
        self.assertIn("new BleSessionJobs(worker)",PROCESSOR)
        block=PROCESSOR.split("public boolean receive(",1)[1].split("public boolean submitModelRefresh(",1)[0]
        self.assertIn("jobs.submit(()->{",block)
        self.assertIn("linkEpoch.matches(epoch)",block)
        self.assertIn("consentEpoch.current(permission)",block)
        self.assertIn("link.isReady()",block)
        self.assertIn("link.sendFrame(answer);",block)
        self.assertNotIn("worker.execute(",block)

    def test_connection_loss_invalidates_and_cancels_requests(self):
        block=PROCESSOR.split("public synchronized void disconnected()",1)[1].split("public boolean receive(",1)[0]
        self.assertIn("setPolicy(InferenceRouter.Mode.OFFLINE,false)",block)
        self.assertIn("resetLink();",block)
        reset=PROCESSOR.split("public synchronized void resetLink()",1)[1].split("public synchronized void disconnected()",1)[0]
        for expression in ("linkEpoch.advance();","jobs.cancelAll();","revokeActive.run();","worker.execute(session::reset);"):
            self.assertIn(expression,reset)
        self.assertIn("aiRequests.disconnected();",SERVICE)
        self.assertIn("aiRequests.resetLink();",SERVICE)
        self.assertIn('message.startsWith("AI BLE ready")',SERVICE)

    def test_service_exit_closes_jobs_and_never_exposes_cloud_background(self):
        self.assertIn("aiRequests.close();",SERVICE)
        self.assertIn("inferenceWorker.shutdown();",SERVICE)
        self.assertIn("new ArrayBlockingQueue<Runnable>(8)",SERVICE)
        self.assertIn("inferenceWorker.purge();",SERVICE)
        self.assertIn("setPolicy(InferenceRouter.Mode.OFFLINE,false)",SERVICE)
        self.assertIn("cloud.clear();",SERVICE)
        self.assertIn("if(cloudConsent!=null && cloudConsent.isChecked())",MAIN)
        self.assertIn("pending.remove(this);",JOBS)
        self.assertIn("task.cancel(true)",JOBS)
        self.assertIn("if(closed)return false",JOBS)

    def test_cloud_and_localhost_requests_abort_on_revocation(self):
        self.assertIn("public synchronized void revokeActive()",CLOUD)
        self.assertIn("public synchronized void revokeActive()",LOCAL)


if __name__=="__main__":
    unittest.main()
