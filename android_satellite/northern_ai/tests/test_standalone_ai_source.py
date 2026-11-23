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
CLOUD_POLICY = (SRC / "CloudModePolicy.java").read_text()
CLOUD_CLIENT = (SRC / "CloudAiClient.java").read_text()
CLOUD_STORE = (SRC / "CloudSettingsStore.java").read_text()


class StandaloneAiSourceTest(unittest.TestCase):
    def test_independent_identity(self):
        self.assertIn("applicationId 'com.northerncircuitstudios.ai'", GRADLE)
        self.assertIn('android:label="${northernAiAppLabel}"', MANIFEST)
        self.assertIn("applicationIdSuffix '.m7memory'", GRADLE)
        self.assertIn("'Northern AI Memory Test'", GRADLE)
        self.assertNotIn("com.flipperai.satellite", MANIFEST)

    def test_launcher_uses_actual_company_emblem(self):
        icon = ROOT / "src/main/res/drawable-nodpi/northern_circuit_company_logo.webp"
        self.assertTrue(icon.is_file(), "The original company emblem must be packaged")
        header = icon.read_bytes()[:16]
        self.assertEqual(header[:4], b"RIFF")
        self.assertEqual(header[8:12], b"WEBP")
        self.assertIn('android:icon="@drawable/northern_circuit_company_logo"', MANIFEST)
        self.assertIn('android:roundIcon="@drawable/northern_circuit_company_logo"', MANIFEST)

    def test_permissions_and_local_network_isolation(self):
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
        self.assertIn("memoryStore.append(new AssistantPolicy.Entry(", ACTIVITY)
        self.assertIn("memoryStore.migrateLegacy(this)", ACTIVITY)

    def test_opt_in_cloud_client_and_secret_handling(self):
        self.assertIn("https://flipper-ai-satellite.guichet-chris94.workers.dev", CLOUD_POLICY)
        self.assertIn("MAX_CLOUD_CHARS = 127", CLOUD_POLICY)
        self.assertIn("Mode.OFFLINE", CLOUD_POLICY)
        self.assertIn("Proxy.NO_PROXY", CLOUD_CLIENT)
        self.assertIn("HttpsURLConnection", CLOUD_CLIENT)
        self.assertIn('setInstanceFollowRedirects(false)', CLOUD_CLIENT)
        self.assertIn('setRequestProperty("X-Device-Token", token)', CLOUD_CLIENT)
        self.assertIn('KeyStore.getInstance("AndroidKeyStore")', CLOUD_STORE)
        self.assertIn("AES/GCM/NoPadding", CLOUD_STORE)
        self.assertIn("CloudSettingsStore.clear(this)", ACTIVITY)
        self.assertIn("CloudSettingsStore.save(this, updated)", ACTIVITY)
        self.assertIn("MODE: OFFLINE", ACTIVITY)
        self.assertIn("CLOUD SETUP", ACTIVITY)
        self.assertIn("TEST CLOUD", ACTIVITY)
        self.assertIn("Choose AI mode", ACTIVITY)
        self.assertIn("I AGREE", ACTIVITY)
        self.assertIn("cloudClient.reply(question, token, allowedCloudMemory)", ACTIVITY)
        self.assertNotIn("GEMINI_API_KEY", ACTIVITY)

    def test_standalone_conversation(self):
        self.assertIn("NORTHERN CIRCUIT AI", ACTIVITY)
        self.assertIn("Qwen3.5-4B-GGUF:Q4_K_M", ACTIVITY)
        self.assertIn("Local AI not running", ACTIVITY)
        self.assertIn("AI replied via ", ACTIVITY)
        self.assertNotIn("FlipperHTTP", ACTIVITY)


if __name__ == "__main__":
    unittest.main()
