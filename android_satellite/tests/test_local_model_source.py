#!/usr/bin/env python3
"""Android loopback inference wiring audit; hardware/runtime tests remain necessary."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
MANIFEST=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
SECURITY=(ROOT/"app/src/main/res/xml/local_model_security.xml").read_text()
UI=(SRC/"MainActivity.java").read_text()
ENGINE=(SRC/"LocalModelEngine.java").read_text()
ROUTER=(SRC/"InferenceRouter.java").read_text()

class LocalModelSourceTest(unittest.TestCase):
    def test_cleartext_only_loopback(self):
        self.assertIn('cleartextTrafficPermitted="false"',SECURITY)
        self.assertIn('<domain>127.0.0.1</domain>',SECURITY)
        self.assertNotIn('<domain includeSubdomains="true">',SECURITY)
        self.assertIn('android:networkSecurityConfig="@xml/local_model_security"',MANIFEST)
        self.assertIn('android:usesCleartextTraffic="false"',MANIFEST)
    def test_no_remote_model_url_or_redirects(self):
        self.assertIn('LocalModelEndpointPolicy.validate(url)',ENGINE)
        self.assertIn('conn.setInstanceFollowRedirects(false)',ENGINE)
        self.assertIn("openConnection(Proxy.NO_PROXY)",ENGINE)
        self.assertIn('conn.setConnectTimeout(',ENGINE)
        self.assertIn('conn.setReadTimeout(',ENGINE)
        self.assertIn('conn.disconnect()',ENGINE)
    def test_local_model_and_diagnostic_fallback_in_ui(self):
        self.assertIn('new OfflineModelOrDiagnostics(localModel,new OfflineDiagnosticEngine())',UI)
        self.assertIn('advancedButton("Use installed offline model"',UI)
        self.assertIn('advancedButton("Disable local model"',UI)
        self.assertIn('advancedButton("Test local model connection"',UI)
        self.assertIn('localModel.answer("Reply with OK.")',UI)
        self.assertIn('inferenceWorker.execute(()->{',UI)
    def test_ble_notification_epoch_bound_before_ui_enqueue(self):
        self.assertIn("final int receivedEpoch=connectionEpoch.current();",UI)
        self.assertIn("connectionEpoch.matches(receivedEpoch) && ble.isReady()",UI)
        self.assertIn("processReceived(text,receivedEpoch)",UI)
        self.assertIn("final int epoch=receivedEpoch;",UI)

    def test_disconnect_during_activity_destroy_does_not_post_to_dead_worker(self):
        self.assertIn("if(!inferenceWorker.isShutdown())",UI)
        self.assertIn("RejectedExecutionException ignored",UI)
        self.assertLess(UI.index("ble.stopScan();transports.close();",UI.index("@Override public void onDestroy()")),
                        UI.index("inferenceWorker.shutdownNow();",UI.index("@Override public void onDestroy()")))

    def test_hybrid_requires_consent(self):
        self.assertIn('if (cloudConsent && cloud.ready())',ROUTER)
        self.assertIn('return local.answer(prompt);',ROUTER)

if __name__=="__main__":unittest.main()
