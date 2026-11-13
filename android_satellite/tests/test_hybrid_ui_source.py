#!/usr/bin/env python3
"""Static guards for chat mode selection and transparent hybrid fallback UX."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"app/src/main/java/com/flipperai/satellite"
CHAT=(ROOT/"AiChatActivity.java").read_text()
ROUTER=(ROOT/"InferenceRouter.java").read_text()

class HybridUiSourceTests(unittest.TestCase):
    def test_display_order_matches_explicit_mode_selection(self):
        self.assertIn("OFFLINE • on this phone", CHAT)
        self.assertIn("HYBRID • offline then allowed cloud", CHAT)
        self.assertIn("ONLINE • requires cloud permission", CHAT)
        self.assertIn("InferenceRouter.Mode.OFFLINE,InferenceRouter.Mode.HYBRID,InferenceRouter.Mode.ONLINE", CHAT)
        self.assertIn("mode=choiceModes[which];", CHAT)
        self.assertNotIn("mode=InferenceRouter.Mode.values()[which]", CHAT)
        self.assertNotIn("setSingleChoiceItems(choices,mode.ordinal()", CHAT)

    def test_changing_modes_invalidates_existing_request(self):
        self.assertIn("if(mode!=chosen)", CHAT)
        self.assertIn("++requestGeneration; // Invalidate any response", CHAT)
        self.assertIn("cloud.revokeActive(); // Disconnect a previous cloud request", CHAT)

    def test_chat_labels_real_response_source(self):
        self.assertIn("router.askWithSource(question,requestedMode,approved)", CHAT)
        self.assertIn("reply.source==InferenceRouter.Source.OFFLINE_FALLBACK", CHAT)
        self.assertIn("HYBRID • Cloud unavailable • On-device fallback", CHAT)
        self.assertIn("reply.source==InferenceRouter.Source.ONLINE", CHAT)
        self.assertIn("On-device response complete", CHAT)
        self.assertIn("Request failed", CHAT)
        self.assertNotIn('setStatus(requestedMode+" • Response complete")', CHAT)

    def test_router_preserves_old_callers(self):
        self.assertIn("return askWithSource(prompt, mode, cloudConsent).text;", ROUTER)
        self.assertIn("if (!cloudConsent) throw new IllegalStateException", ROUTER)

if __name__=="__main__":
    unittest.main()
