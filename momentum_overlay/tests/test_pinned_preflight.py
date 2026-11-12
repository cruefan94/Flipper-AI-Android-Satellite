#!/usr/bin/env python3
"""Pinned-source preflight tests, no firmware compilation."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE=Path(__file__).resolve().parents[1]/"preflight_pinned_source.py"
spec=importlib.util.spec_from_file_location("pinned_preflight", MODULE)
preflight=importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)

class PinnedPreflightTest(unittest.TestCase):
    def test_known_git_blob_hash(self):
        self.assertEqual(preflight.git_blob_sha(b"hello\n"),
                         "ce013625030ba8dba906f756967f9e9ca394464a")
    def test_clean_exact_file_accepted(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            rel="targets/f7/ble_glue/gap.c"
            path=root/rel
            path.parent.mkdir(parents=True)
            path.write_bytes(b"fixed sample\n")
            with patch.dict(preflight.EXPECTED_GIT_BLOBS,
                            {rel:preflight.git_blob_sha(path.read_bytes())},clear=True):
                self.assertEqual(preflight.verify(root),[])
                path.write_bytes(b"modified\n")
                self.assertEqual(len(preflight.verify(root)),1)
                path.unlink()
                self.assertEqual(len(preflight.verify(root)),1)
    def test_pinned_commit_is_explicit(self):
        self.assertEqual(len(preflight.PINNED_MOMENTUM_COMMIT),40)
        self.assertEqual(len(preflight.EXPECTED_GIT_BLOBS),8)

if __name__=="__main__":
    unittest.main()
