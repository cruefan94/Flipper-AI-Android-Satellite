"""Fail-closed host checks for Phase 2 Android release isolation.

These are source-policy checks, not proof of APK certificates or device safety.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/phase2-android-verified-variants.yml"


class ReleaseIsolationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_no_release_signing_or_install(self):
        source = self.workflow.lower()
        for forbidden in ("assemblerelease", "adb install", "bundletool install", "keystore_password", "signingkey", "playstore"):
            self.assertNotIn(forbidden, source)

    def test_all_three_variants_are_debug_and_separately_built(self):
        source = self.workflow
        self.assertEqual(source.count(":app:assembleDebug"), 3)
        self.assertEqual(source.count(":app:clean :app:assembleDebug"), 2)
        self.assertEqual(source.count('apksigner" verify --verbose'), 3)
        self.assertIn("-PsatelliteSideBySide=true -PsatelliteNavigationTest=true", source)
        self.assertIn("-PsatelliteSideBySide=true :app:clean", source)

    def test_no_claim_of_stable_upgrade_or_hardware_validation(self):
        source = self.workflow
        self.assertIn("NOT tested on phone or Flipper", source)
        self.assertIn("without stable release key", source)
        self.assertIn("UNTESTED", source)
        self.assertIn("persist-credentials: false", source)
        self.assertIn("permissions:\n  contents: read", source)


if __name__ == "__main__":
    unittest.main()
