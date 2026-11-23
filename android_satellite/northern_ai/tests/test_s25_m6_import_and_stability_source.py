#!/usr/bin/env python3
"""Source gates for S25 M6 stability preview; not a phone performance/inference test."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/northerncircuitstudios/ai"
STORE = (JAVA / "ImportedGgufStore.java").read_text()
MODELS = (JAVA / "ModelsActivity.java").read_text()
ACTIVITY = (JAVA / "AssistantActivity.java").read_text()
LOCAL = (JAVA / "LocalAiClient.java").read_text()
POLICY = (JAVA / "AssistantPolicy.java").read_text()
GRADLE = (ROOT / "build.gradle").read_text()
MANIFEST = (ROOT / "src/main/AndroidManifest.xml").read_text()


class S25M6SourceTests(unittest.TestCase):
    def test_separate_s25_preview_identity(self):
        self.assertIn("applicationIdSuffix '.m7memory'", GRADLE)
        self.assertIn("'Northern AI Memory Test'", GRADLE)
        self.assertIn('android:screenOrientation="portrait"', MANIFEST)

    def test_multiple_user_selected_models_no_broad_storage_permission(self):
        for marker in ["Intent.ACTION_OPEN_DOCUMENT", "Intent.EXTRA_ALLOW_MULTIPLE",
                       "Intent.CATEGORY_OPENABLE", "getClipData()",
                       "ImportedGgufStore.MAX_BATCH", "background.execute(() ->",
                       "private void refreshImportedFiles()", "delete(this, model.name)"]:
            self.assertIn(marker, MODELS)
        for marker in ["getContentResolver().openInputStream(uri)",
                       'new File(context.getFilesDir(), "models")',
                       'File.createTempFile(".gguf-import-", ".part", dir)',
                       "getAvailableBytes()", "MAX_SIZE", "STORAGE_RESERVE",
                       "cleanName", "destination.exists()", "staging.renameTo(destination)",
                       "header.read() != 'G'", "delete(Context context"]:
            self.assertIn(marker, STORE)
        self.assertNotIn("android.permission.MANAGE_EXTERNAL_STORAGE", MANIFEST)
        self.assertNotIn("android.permission.READ_EXTERNAL_STORAGE", MANIFEST)
        self.assertIn("Native loading is not yet enabled", MODELS)

    def test_cancel_and_incremental_chat_render(self):
        for marker in ['send.setText("STOP")', 'send.setText("SEND")',
                       "send.setOnClickListener(v -> { if (answering) cancelAnswer(); else ask(); });",
                       "activeGenerationTask.cancel(true)",
                       "client.cancel();", "cloudClient.cancel();",
                       "private void appendMessage(", "private void appendNewestMessage()",
                       "appendNewestMessage();", "if (request != generation"]:
            self.assertIn(marker, ACTIVITY)
        self.assertIn("volatile int generation", ACTIVITY)

    def test_empty_qwen_text_handling_is_explicit(self):
        self.assertIn('.put("max_tokens", 768)', LOCAL)
        self.assertIn('.put("enable_thinking", false)', LOCAL)
        self.assertIn("Local AI used its response limit", LOCAL)
        self.assertIn("Local AI produced no final text", LOCAL)
        self.assertIn("MAX_CONTEXT_CHARS = 2800", POLICY)

    def test_no_claim_native_inference_is_implemented(self):
        self.assertIn("llama.cpp server should use", MODELS)
        self.assertIn("Imported, not loaded", MODELS)
        self.assertIn("not necessarily runnable", MODELS)


if __name__ == "__main__":
    unittest.main()
