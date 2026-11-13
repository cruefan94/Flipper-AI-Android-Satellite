#!/usr/bin/env python3
"""Static model package and IME stability guards. Android interaction is unverified."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
CHAT=(SRC/"AiChatActivity.java").read_text()
MODEL=(SRC/"ModelsActivity.java").read_text()
STORE=(SRC/"EmbeddedModelStore.java").read_text()
EXTRACTOR=(SRC/"ModelPackageExtractor.java").read_text()
BLE=(SRC/"MainActivity.java").read_text()

class ModelPackageImeTests(unittest.TestCase):
    def test_model_zip_is_data_not_recompiled_apk(self):
        self.assertIn("ModelPackageExtractor.supportsZipName(name)",STORE)
        self.assertIn("ModelPackageExtractor.extract(input,output)",STORE)
        self.assertIn("StandardCopyOption.ATOMIC_MOVE",STORE)
        self.assertIn("if(staging.exists())staging.delete()",STORE)
        self.assertIn("if(copied==0)",STORE)
        self.assertIn("MAX_MODEL_BYTES",EXTRACTOR)
        self.assertIn("MAX_ENTRIES",EXTRACTOR)
        self.assertIn("checkPath(name)",EXTRACTOR)
        self.assertIn("seenModel",EXTRACTOR)
        self.assertIn("exactly one .litertlm",EXTRACTOR)
        self.assertNotIn("Runtime.getRuntime()",EXTRACTOR)
        self.assertNotIn("ProcessBuilder",EXTRACTOR)
        self.assertNotIn("DexClassLoader",EXTRACTOR)
        self.assertIn("IMPORT .LITERTLM OR MODEL ZIP",MODEL)
        self.assertIn("No APK compilation needed",MODEL)
        self.assertIn("Import offline model (.litertlm or ZIP)",BLE)

    def test_ime_single_owner_and_padding_gated(self):
        self.assertIn("SOFT_INPUT_ADJUST_NOTHING",CHAT)
        self.assertIn("SOFT_INPUT_ADJUST_RESIZE",CHAT)
        self.assertIn("WindowInsets.Type.ime()",CHAT)
        self.assertIn("Math.max(bars.bottom,ime.bottom)",CHAT)
        self.assertIn("view.getPaddingBottom()!=nextBottom",CHAT)
        self.assertIn("if(view.getPaddingLeft()!=left",CHAT)
        self.assertIn("page.setFocusableInTouchMode(true)",CHAT)
        self.assertIn("page.requestFocus();",CHAT)
        self.assertIn("prompt.setOnFocusChangeListener(",CHAT)
        self.assertIn("hideSoftInputFromWindow",CHAT)
        self.assertIn("chatScroll.setSmoothScrollingEnabled(false)",CHAT)
        self.assertIn("send.setOnClickListener(v->ask())",CHAT)

if __name__=="__main__":
    unittest.main()
