#!/usr/bin/env python3
"""Privacy and restart behavior checks for saved offline model preference.

Source audit only: runtime Android SharedPreferences need device testing.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
UI=(ROOT/"app/src/main/java/com/flipperai/satellite/MainActivity.java").read_text()

class OfflinePreferenceSourceTest(unittest.TestCase):
    def test_preference_is_only_content_addressed_filename(self):
        self.assertIn('private static final String OFFLINE_MODEL_PREF = "preferred_offline_task"',UI)
        self.assertIn('file.getName().matches("[0-9a-f]{64}\\\\.task")',UI)
        self.assertIn('putString(OFFLINE_MODEL_PREF,file.getName())',UI)
        self.assertIn('getString(OFFLINE_MODEL_PREF, "")',UI)
        self.assertIn('savedModels.setSelection(i);',UI)
    def test_native_model_cleanup_is_off_ui_thread(self):
        self.assertIn('new Thread(closingModel::close,"flipper-ai-model-cleanup")',UI)
        self.assertIn('cleanup.setDaemon(true);',UI)
        self.assertIn('inferenceWorker.shutdownNow();',UI)
        self.assertNotIn('if(embeddedModel!=null)embeddedModel.close();',UI)

    def test_cloud_consent_is_never_restored(self):
        self.assertIn("cloudConsent.setChecked(false);",UI)
        self.assertNotIn('putBoolean("cloudConsent"',UI)
        self.assertNotIn('getBoolean("cloudConsent"',UI)
    def test_remember_only_after_successful_model_loading(self):
        self.assertIn('embeddedModel.load(imported.file);\n        selectedModel.select',UI)
        self.assertIn('rememberPreferredModel(imported.file);',UI)
        self.assertIn('embeddedModel.load(chosen);\n          selectedModel.select',UI)
        self.assertIn('rememberPreferredModel(chosen);',UI)
        self.assertIn('remove(OFFLINE_MODEL_PREF).apply();',UI)

if __name__=="__main__":unittest.main()
