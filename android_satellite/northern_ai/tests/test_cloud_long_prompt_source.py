#!/usr/bin/env python3
"""Static source checks for Cloudflare /ask-v2 client contract; no live cloud call."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
POLICY = (ROOT / "src/main/java/com/northerncircuitstudios/ai/CloudModePolicy.java").read_text()
CLIENT = (ROOT / "src/main/java/com/northerncircuitstudios/ai/CloudAiClient.java").read_text()
ACTIVITY = (ROOT / "src/main/java/com/northerncircuitstudios/ai/AssistantActivity.java").read_text()

class CloudExtendedPromptSourceTests(unittest.TestCase):
    def test_legacy_short_message_compatible(self):
        self.assertIn("LEGACY_MAX_CLOUD_CHARS = 127", POLICY)
        self.assertIn("MAX_CLOUD_CHARS = 1600", POLICY)
        self.assertIn('open(extended ? "/ask-v2" : "/ask")', CLIENT)
        self.assertIn('text.length() > CloudModePolicy.LEGACY_MAX_CLOUD_CHARS', CLIENT)

    def test_errors_explain_deployment_not_repeated_retries(self):
        self.assertIn('if (status == 404 && extended)', CLIENT)
        self.assertIn('new /ask-v2 endpoint is not deployed yet', CLIENT)
        self.assertIn('if (status == 400 && extended)', CLIENT)
        self.assertIn('body.length > 8192', CLIENT)

    def test_keeps_opt_in_cloud_and_legacy_flipper(self):
        self.assertIn('CloudModePolicy.validateToken(token)', CLIENT)
        self.assertIn('CloudModePolicy.validateCloudPrompt(prompt)', CLIENT)
        self.assertIn('connection.setRequestProperty("X-Device-Token", token)', CLIENT)
        self.assertIn('cloudClient.reply(question, token, allowedCloudMemory)', ACTIVITY)
        self.assertIn('CloudModePolicy.Mode.OFFLINE', ACTIVITY)

if __name__ == "__main__":
    unittest.main()
