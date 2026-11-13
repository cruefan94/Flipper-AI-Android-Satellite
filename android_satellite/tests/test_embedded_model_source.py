#!/usr/bin/env python3
"""Static safety checks for in-APK offline LLM path; real inference needs an Android phone."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/"app"
SRC=APP/"src/main/java/com/flipperai/satellite"
GRADLE=(APP/"build.gradle").read_text()
ENGINE=(SRC/"EmbeddedLlmEngine.kt").read_text()
STORE=(SRC/"EmbeddedModelStore.java").read_text()
UI=(SRC/"MainActivity.java").read_text()
PREFERRED=(SRC/"EmbeddedOrLocalEngine.java").read_text()
MANIFEST=(APP/"src/main/AndroidManifest.xml").read_text()

class EmbeddedModelSourceTests(unittest.TestCase):
    def test_official_embedded_runtime_is_compiled(self):
        self.assertIn("com.google.ai.edge.litertlm:litertlm-android:0.18.0",GRADLE)
        self.assertIn("new EmbeddedLlmEngine(",UI)
        self.assertIn("EngineConfig(",ENGINE)
        self.assertIn("candidate.initialize()",ENGINE)
        self.assertIn("sendMessage(prompt)",ENGINE)

    def test_model_weights_are_imported_not_bundled(self):
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT",UI)
        self.assertIn("EmbeddedModelStore.importFromDocument(",UI)
        self.assertIn("getFilesDir()",STORE)
        self.assertIn("FileOutputStream(staging)",STORE)
        self.assertIn("StandardCopyOption.ATOMIC_MOVE",STORE)
        self.assertNotIn("new URL(",STORE)
        self.assertNotIn("openConnection(",STORE)

    def test_inference_is_not_on_ui_thread(self):
        self.assertIn("Looper.myLooper() != Looper.getMainLooper()",ENGINE)
        self.assertIn('inferenceWorker.execute(()->{',UI)
        self.assertIn("embeddedModel.answer(",UI)
        self.assertIn("oldConversation?.close()",ENGINE)
        self.assertIn("oldEngine?.close()",ENGINE)

    def test_embedded_priority_and_cloud_consent_not_bypassed(self):
        self.assertIn("embedded.ready()?embedded.answer(prompt):",PREFERRED)
        self.assertIn("new EmbeddedOrLocalEngine(embeddedModel,localModel)",UI)
        self.assertIn('Verified LIVE embedded offline model response:',UI)
        self.assertIn('android:usesCleartextTraffic="false"',MANIFEST)

    def test_model_import_fails_closed(self):
        self.assertIn('!"content".equals(uri.getScheme())',STORE)
        self.assertIn('endsWith(".litertlm")',STORE)
        self.assertIn("MAX_BYTES",STORE)
        self.assertIn("if(copied==0)",STORE)
        self.assertIn("if(staging.exists())staging.delete();",STORE)

if __name__=="__main__":unittest.main()
