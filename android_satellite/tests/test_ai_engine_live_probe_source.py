#!/usr/bin/env python3
"""Source guards for Android explicit engine live tests and error classification.

Requires later real phone tests with an actual .litertlm model and a configured
consented cloud Worker; a source test alone is not inference evidence.
"""
from pathlib import Path
import unittest

SOURCE=(Path(__file__).resolve().parents[1]/
    "app/src/main/java/com/flipperai/satellite/AiChatActivity.java").read_text()

class EngineProbes(unittest.TestCase):
    def test_probe_is_manual_and_separate_from_chat_send(self):
        self.assertIn('smallAction(voiceControls,"TEST AI",this::chooseEngineSelfTest)',SOURCE)
        self.assertIn("private void chooseEngineSelfTest()",SOURCE)
        self.assertIn("private void testEngineLive(boolean online)",SOURCE)
        self.assertIn('"TEST EMBEDDED OFFLINE • no internet"',SOURCE)
        self.assertIn('"TEST ONLINE WORKER • sends a fixed test prompt"',SOURCE)

    def test_offline_probe_uses_real_model_not_diagnostic_rule(self):
        block=SOURCE.split("private void testEngineLive(",1)[1].split(
            "  private void cloudSettings()",1)[0]
        self.assertIn("EmbeddedModelStore.installed(this)",block)
        self.assertIn('embedded.answer("Reply with OK.")',block)
        self.assertNotIn("OfflineDiagnosticEngine",block)
        self.assertIn("worker.execute(()->{",block)
        self.assertIn("if(!embedded.ready())",block)
        self.assertIn('Verified LIVE embedded offline response',block)

    def test_online_probe_requires_explicit_permission_and_only_fixed_prompt(self):
        block=SOURCE.split("private void testEngineLive(",1)[1].split(
            "  private void cloudSettings()",1)[0]
        self.assertIn("if(online && (!consent || !cloud.ready()))",block)
        self.assertIn("final boolean consentAtStart=consent;",block)
        self.assertIn('router.ask("Reply with OK.",InferenceRouter.Mode.ONLINE,consentAtStart)',block)
        self.assertIn("(!online || (consentAtStart && consent))",block)
        self.assertIn("generation==requestGeneration",block)
        self.assertNotIn("InferenceRouter.Mode.HYBRID",block)
        self.assertNotIn("prompt.getText()",block)

    def test_failures_are_not_synthesized_as_ai_replies_or_spoken(self):
        ask=SOURCE.split("private void ask(){",1)[1].split(
            "private void append(",1)[0]
        self.assertIn("boolean succeeded=false;",ask)
        self.assertIn("succeeded=true;",ask)
        self.assertIn("final boolean responseSucceeded=succeeded;",ask)
        self.assertIn("if(responseSucceeded){",ask)
        self.assertIn('append("SYSTEM",answer);',ask)
        self.assertIn('setStatus(requestedMode+" • Request failed");',ask)
        speaking=ask.split("if(responseSucceeded){",1)[1].split("}else{",1)[0]
        self.assertIn("speech.speak(",speaking)
        error_branch=ask.split("}else{",1)[1]
        self.assertNotIn("speech.speak(",error_branch)
        self.assertNotIn('setStatus(requestedMode+" • Response complete");',error_branch)

    def test_imported_model_is_not_claimed_live_verified(self):
        self.assertIn("Model imported • Inference not yet verified on this phone",SOURCE)
        self.assertNotIn("Model imported • Ready for AI chat",SOURCE)

if __name__=="__main__":unittest.main()
