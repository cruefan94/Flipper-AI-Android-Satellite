"""Fail-closed source contract for APK verification; does not touch devices or secrets."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/phase2-android-verified-variants.yml"

class ApkVerificationContractTest(unittest.TestCase):
    def setUp(self):
        self.source = WORKFLOW.read_text(encoding="utf-8")

    def test_signature_and_binary_manifest_checked_before_upload(self):
        s = self.source
        verify = s.index('apksigner" verify --verbose "$APK"')
        badging = s.index('aapt" dump badging "$APK"')
        identity = s.index("name='com.flipperai.satellite' versionCode=")
        label = s.index("application-label:'Flipper AI Satellite'")
        upload = s.index('Upload the single combined Android APK and integrity report')
        self.assertLess(verify, badging)
        self.assertLess(badging, identity)
        self.assertLess(identity, label)
        self.assertLess(label, upload)
        self.assertIn('set -euo pipefail', s)

    def test_no_install_or_production_signing_and_preserve_data_warning(self):
        s = self.source
        self.assertIn('Do not uninstall the working app', s)
        self.assertIn('Debug signature may differ from the installed app', s)
        self.assertIn('UNTESTED', s)
        self.assertNotIn('adb install', s)
        self.assertNotIn('apksigner sign ', s)
        self.assertNotIn('gradle :app:install', s)

    def test_evidence_tied_to_checked_apk_and_head(self):
        s = self.source
        self.assertIn("source != os.environ['HEAD_SOURCE_SHA']", s)
        self.assertIn("'sha256': hashlib.sha256(data).hexdigest()", s)
        self.assertIn("sha256sum -c SHA256SUMS.txt", s)
        self.assertIn("if len(binaries) != 1:", s)

if __name__ == "__main__":
    unittest.main()
