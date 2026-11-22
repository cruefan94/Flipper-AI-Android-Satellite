"""Phase 2 online AI safeguards: source-level guards, not device/network tests."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
MAIN=(SRC/"MainActivity.java").read_text(encoding="utf-8")
HANDLER=(SRC/"SatelliteRequestHandler.java").read_text(encoding="utf-8")
SERVICE=(SRC/"BleConnectionService.java").read_text(encoding="utf-8")
WORKER=(SRC/"WorkerCloudEngine.java").read_text(encoding="utf-8")

class Phase2OnlineAiContract(unittest.TestCase):
    def test_cloud_mode_and_consent_are_visible_not_hidden(self):
        self.assertIn('SatelliteUi.sectionTitle(this,root,"FLIPPER AI MODE")',MAIN)
        self.assertIn('root.addView(mode);',MAIN)
        self.assertIn('root.addView(cloudConsent);',MAIN)
        self.assertIn('root.addView(aiModeStatus);',MAIN)
        self.assertIn("Cloud is unavailable.",MAIN)
        self.assertIn("Worker/model availability is not yet verified.",MAIN)
        self.assertEqual(MAIN.count("mode=new Spinner(this);"),1)
        self.assertEqual(MAIN.count("cloudConsent=new CheckBox(this);"),1)

    def test_only_explicit_cloud_test_does_network_work(self):
        self.assertIn('TEST ONLINE AI (SENDS ONE TEST PROMPT)',MAIN)
        self.assertIn('cloudConsent.isChecked()',MAIN)
        self.assertIn('CloudCredentialStore.load(this)',MAIN)
        self.assertIn('cloud.answer("Reply with OK to confirm your online AI works.")',MAIN)
        self.assertIn('cloud.revokeActive();',MAIN)
        self.assertIn('leaveAiSettingsScreen()',MAIN)

    def test_flipper_answer_has_actual_provider_provenance(self):
        self.assertIn("router.askWithSource(fields[3],mode,cloudConsent)",HANDLER)
        self.assertIn("InferenceRouter.Source.ONLINE",HANDLER)
        self.assertIn("InferenceRouter.Source.OFFLINE_FALLBACK",HANDLER)
        self.assertIn('"CLOUD: "',HANDLER)
        self.assertIn('"OFFLINE FALLBACK: "',HANDLER)
        self.assertIn('"OFFLINE: "',HANDLER)
        self.assertIn('return fit("ANSWER",id,origin+outcome.text,maxFrameBytes);',HANDLER)

    def test_phone_keeps_all_cloud_credentials_and_revocation(self):
        self.assertIn('setBothAiPolicies(InferenceRouter.Mode.OFFLINE,false)',SERVICE)
        self.assertIn('if(rpcAiRequests!=null)rpcAiRequests.setPolicy(mode,consent);',SERVICE)
        self.assertIn('cloud.configure(credentials.endpoint,credentials.token);',SERVICE)
        self.assertIn('conn.setInstanceFollowRedirects(false);',WORKER)
        self.assertIn('WorkerEndpointPolicy.validate(url,deviceToken)',WORKER)
        self.assertIn('revokeActive();',WORKER)

if __name__=="__main__":
    unittest.main()
