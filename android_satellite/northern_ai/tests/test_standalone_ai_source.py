#!/usr/bin/env python3
"""Static privacy/build gates; not proof of real-world model behavior."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
GRADLE = (ROOT / "build.gradle").read_text()
MANIFEST = (ROOT / "src/main/AndroidManifest.xml").read_text()
SECURITY = (ROOT / "src/main/res/xml/loopback_security.xml").read_text()
SRC = ROOT / "src/main/java/com/northerncircuitstudios/ai"
POLICY = (SRC / "AssistantPolicy.java").read_text()
CLIENT = (SRC / "LocalAiClient.java").read_text()
ACTIVITY = (SRC / "AssistantActivity.java").read_text()
STORE = (SRC / "EncryptedChatStore.java").read_text()


class StandaloneAiSourceTest(unittest.TestCase):
    def test_independent_identity(self):
        self.assertIn("applicationId 'com.northerncircuitstudios.ai'", GRADLE)
        self.assertIn('android:label="Northern Circuit AI"', MANIFEST)
        self.assertNotIn("com.flipperai.satellite", MANIFEST)

    def test_no_spurious_permissions_or_cloud(self):
        self.assertIn("android.permission.INTERNET", MANIFEST)
        for permission in ("BLUETOOTH", "RECORD_AUDIO", "LOCATION",
                           "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"):
            self.assertNotIn("android.permission." + permission, MANIFEST)
        self.assertIn('android:allowBackup="false"', MANIFEST)
        self.assertIn('android:usesCleartextTraffic="false"', MANIFEST)
        self.assertIn('<domain includeSubdomains="false">127.0.0.1</domain>', SECURITY)
        self.assertIn('<base-config cleartextTrafficPermitted="false"/>', SECURITY)
        self.assertNotIn("workers.dev", CLIENT)
        self.assertNotIn("https://", CLIENT)

    def test_local_model_client(self):
        self.assertIn("http://127.0.0.1:9931/v1/chat/completions", POLICY)
        self.assertIn("Proxy.NO_PROXY", CLIENT)
        self.assertIn("setInstanceFollowRedirects(false)", CLIENT)
        self.assertIn('"stream", false', CLIENT)
        self.assertIn("AssistantPolicy.recentContext", CLIENT)

    def test_private_memory_and_explicit_erase(self):
        self.assertIn('KeyStore.getInstance("AndroidKeyStore")', STORE)
        self.assertIn("AES/GCM/NoPadding", STORE)
        self.assertIn("EncyptedChatStore", STORE.replace("EncryptedChatStore", "EncyptedChatStore"))
        self.assertIn("EncryptedChatStore.clear(this)", ACTIVITY)
        self.assertIn("EncryptedChatStore.save(this, snapshot)", ACTIVITY)

    def test_standalone_conversation(self):
        self.assertIn("NORTHERN CIRCUIT AI", ACTIVITY)
        self.assertIn("Qwen3.5-4B-GGUF:Q4_K_M", ACTIVITY)
        self.assertIn("Local AI not running", ACTIVITY)
        self.assertIn("Verified model response", ACTIVITY)
        self.assertNotIn("FlipperHTTP", ACTIVITY)


if __name__ == "__main__":
    unittest.main()
