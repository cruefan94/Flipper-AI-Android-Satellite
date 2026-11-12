#!/usr/bin/env python3
"""Non-destructive staging tests; not a Momentum firmware build."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
spec=importlib.util.spec_from_file_location("stage_overlay",ROOT/"stage_pinned_overlay.py")
stager=importlib.util.module_from_spec(spec)
spec.loader.exec_module(stager)

class StagePinnedOverlayTest(unittest.TestCase):
    def test_requires_all_seven_gatt_policy_sources(self):
        self.assertEqual(len(stager.SERVICE_FILES),7)
        self.assertIn("ai_satellite_replay.h",stager.SERVICE_FILES)
        self.assertIn("ai_satellite_trust.h",stager.SERVICE_FILES)
    def test_unchanged_input_and_no_output_on_validation_failure(self):
        with tempfile.TemporaryDirectory() as dirname:
            root=Path(dirname)/"upstream"
            root.mkdir()
            output=Path(dirname)/"generated"
            with self.assertRaises(ValueError):
                stager.stage(root,output)
            self.assertFalse(output.exists())
            self.assertEqual(list(root.iterdir()),[])
    def test_stage_rejects_output_inside_input_tree(self):
        with tempfile.TemporaryDirectory() as dirname:
            source=Path(dirname)/"original"
            source.mkdir()
            with self.assertRaises(ValueError):
                stager.stage(source,source/"patched")
            self.assertFalse((source/"patched").exists())

    def test_staging_is_separate_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as dirname:
            root=Path(dirname)/"upstream"
            root.mkdir()
            fixture=root/"keep.txt"
            fixture.write_text("unchanged")
            output=Path(dirname)/"generated"
            with patch.object(stager,"prepare",return_value={
                "targets/f7/ble_glue/services/ai_satellite_service.h":b"safe sample\n"}):
                self.assertEqual(stager.stage(root,output),1)
            self.assertEqual(fixture.read_text(),"unchanged")
            self.assertEqual((output/"targets/f7/ble_glue/services/ai_satellite_service.h").read_bytes(),b"safe sample\n")
            self.assertIn("NOT", (output/"OVERLAY_UNTESTED.txt").read_text())
            with self.assertRaises(FileExistsError):
                stager.stage(root,output)

if __name__=="__main__":
    unittest.main()
