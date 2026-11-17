"""Host-only regression for Phase 2 artifact provenance (no APK/device access)."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/phase2-android-verified-variants.yml"


class Phase2ProvenanceContractTest(unittest.TestCase):
    def test_distinguishes_checkout_from_pr_head(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("'built_checkout_sha': source", source)
        self.assertIn("'pull_request_head_sha': os.environ['HEAD_SOURCE_SHA']", source)
        self.assertIn("github.event.pull_request.head.sha || github.sha", source)
        self.assertIn("git', 'rev-parse', 'HEAD'", source)

    def test_checks_three_separate_package_identities(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        for package in (
            "com.flipperai.satellite'",
            "com.flipperai.satellite.test'",
            "com.flipperai.satellite.navtest'",
        ):
            self.assertIn(package, source)
        self.assertEqual(source.count('apksigner" verify'), 3)

    def test_checksums_and_no_production_signing(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("sha256sum -c SHA256SUMS.txt", source)
        self.assertIn("'sha256': hashlib.sha256(data).hexdigest()", source)
        self.assertIn("CI compiled and signed-debug-package-verified", source)
        self.assertIn("UNTESTED", source)


if __name__ == "__main__":
    unittest.main()
