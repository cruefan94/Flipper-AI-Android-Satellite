"""Host-only single-APK provenance and safety contract (no hardware access)."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/phase2-android-verified-variants.yml"
BUILD_CONFIG = ROOT / "android_satellite/app/build.gradle"


class SingleApkProvenanceTest(unittest.TestCase):
    def test_records_exact_source_head_and_integrity(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("'built_checkout_sha': source", source)
        self.assertIn("'pull_request_head_sha': os.environ['HEAD_SOURCE_SHA']", source)
        self.assertIn("github.event.pull_request.head.sha || github.sha", source)
        self.assertIn("git', 'rev-parse', 'HEAD'", source)
        self.assertIn("source != os.environ['HEAD_SOURCE_SHA']", source)
        self.assertIn("sha256sum -c SHA256SUMS.txt", source)
        self.assertIn("'sha256': hashlib.sha256(data).hexdigest()", source)

    def test_one_combined_android_apk_not_three_variants(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("one full-feature Android APK", source)
        self.assertIn("variant_policy': 'one_combined_apk_only'", source)
        self.assertIn("len(binaries) != 1", source)
        self.assertIn("FlipperAI-Satellite-ALL-FEATURES-DEBUG-UNTESTED.apk", source)
        self.assertEqual(source.count('apksigner" verify'), 1)
        self.assertEqual(source.count(":app:assembleDebug"), 1)
        self.assertIn("com.flipperai.satellite' versionCode=", source)
        self.assertNotIn("-PsatelliteSideBySide=true", source)
        self.assertNotIn("-PsatelliteNavigationTest=true", source)
        self.assertNotIn("com.flipperai.satellite.navtest", source)
        self.assertNotIn("com.flipperai.satellite.test", source)

    def test_preserves_existing_app_data_and_no_production_signing_claim(self):
        source = WORKFLOW.read_text(encoding="utf-8")
        gradle = BUILD_CONFIG.read_text(encoding="utf-8")
        self.assertIn("CI compiled and signed-debug-package-verified", source)
        self.assertIn("Do not uninstall the working app", source)
        self.assertIn("UNTESTED", source)
        self.assertIn("app-private model data and preferences may be lost", source)
        self.assertIn("applicationId 'com.flipperai.satellite'", gradle)
        self.assertIn("Without it, Gradle uses its normal per-run debug key", gradle)


if __name__ == "__main__":
    unittest.main()
