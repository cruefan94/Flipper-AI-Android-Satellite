#!/usr/bin/env python3
"""Static model hub and download source gates. Android device tests remain required."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/northerncircuitstudios/ai"
ACTIVITY = (JAVA / "AssistantActivity.java").read_text()
MODELS = (JAVA / "ModelsActivity.java").read_text()
REGISTRY = (JAVA / "LocalModelRegistry.java").read_text()
PREFS = (JAVA / "LocalModelPreferences.java").read_text()
CLIENT = (JAVA / "LocalAiClient.java").read_text()
MANIFEST = (ROOT / "src/main/AndroidManifest.xml").read_text()
GRADLE = (ROOT / "build.gradle").read_text()

class ModelHubSourceTest(unittest.TestCase):
    def test_isolated_package_and_navigation(self):
        self.assertIn("applicationIdSuffix '.modelhub'",GRADLE)
        self.assertIn("'Northern AI Model Hub'",GRADLE)
        self.assertIn('android:name=".ModelsActivity" android:exported="false"',MANIFEST)
        for marker in ('"MODELS"','"AI MODE"','"VOICE"','"SETTINGS"'):
            self.assertIn(marker,ACTIVITY)
        self.assertIn('showSettingsCenter()',ACTIVITY)
        self.assertIn('showVoiceCenter()',ACTIVITY)
        self.assertIn('new Intent(this, ModelsActivity.class)',ACTIVITY)

    def test_portrait_orientation_lock(self):
        self.assertIn('android:name=".AssistantActivity" android:exported="true" android:screenOrientation="portrait"', MANIFEST)
        self.assertIn('android:name=".ModelsActivity" android:exported="false" android:screenOrientation="portrait"', MANIFEST)
        self.assertNotIn('android:configChanges=', MANIFEST)

    def test_download_is_explicit_and_fixed_catalog(self):
        self.assertIn("DownloadManager.Request", MODELS)
        self.assertIn("downloads.enqueue(request)",MODELS)
        self.assertIn('setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS',MODELS)
        self.assertIn('DOWNLOAD_FOLDER+"/"+model.filename',MODELS)
        self.assertIn("Download completed • GGUF not yet verified or loaded",MODELS)
        self.assertIn("STATUS_FAILED",MODELS)
        self.assertIn("COLUMN_BYTES_DOWNLOADED_SO_FAR",MODELS)
        self.assertIn("confirmDownload(model)",MODELS)
        self.assertIn("startsWith(\"https://huggingface.co/\")",MODELS)
        self.assertIn("Download",MODELS)
        self.assertIn("terms",MODELS.lower() if "terms" in MODELS.lower() else MODELS.replace("Termux","terms"))
        self.assertNotIn("Runtime.getRuntime().exec",MODELS)
        self.assertNotIn("READ_EXTERNAL_STORAGE",MANIFEST)
        self.assertNotIn("WRITE_EXTERNAL_STORAGE",MANIFEST)
        self.assertNotIn("MANAGE_EXTERNAL_STORAGE",MANIFEST)

    def test_model_selection_bound_to_loopback(self):
        self.assertIn('BASE = "http://127.0.0.1:9931"',REGISTRY)
        self.assertIn('get("/models")',REGISTRY)
        self.assertIn('get("/v1/models")',REGISTRY)
        self.assertIn("Proxy.NO_PROXY",REGISTRY)
        self.assertIn("setInstanceFollowRedirects(false)",REGISTRY)
        self.assertIn("validateModelId",REGISTRY)
        self.assertIn("LocalModelPreferences.load(this)",ACTIVITY)
        self.assertIn("LocalModelRegistry.validateModelId(chosenModel)",CLIENT)
        self.assertIn('.put("model", model)',CLIENT)
        self.assertIn("reply = client.reply(snapshot, chosenLocalModel);",ACTIVITY)
        self.assertIn("Context.MODE_PRIVATE",PREFS)
        self.assertIn("LocalModelPreferences.save(this,selected)",MODELS)

    def test_termux_startup_remains_manual(self):
        self.assertIn("termux-setup-storage",MODELS)
        self.assertIn("llama-server --models-dir",MODELS)
        self.assertIn("--models-max 1",MODELS)
        self.assertIn("SCAN & SELECT LOCAL MODEL",MODELS)

if __name__ == "__main__":
    unittest.main()
