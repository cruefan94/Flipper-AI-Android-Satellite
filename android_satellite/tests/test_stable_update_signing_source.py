#!/usr/bin/env python3
"""Stable Android update candidate must not consume keys on untrusted branches."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
WORKFLOW=(ROOT/".github/workflows/android-apk.yml").read_text()
GRADLE=(ROOT/"android_satellite/app/build.gradle").read_text()

class StableUpdateGate(unittest.TestCase):
    def test_debug_build_remains_available_without_release_secrets(self):
        self.assertIn(":app:assembleDebug",WORKFLOW)
        self.assertIn('echo "STABLE_SIGNING_ENABLED=false" >> "$GITHUB_ENV"',WORKFLOW)
        self.assertIn("FlipperAI-Satellite-DEBUG-UNTESTED",WORKFLOW)
        self.assertIn("versionCode packageCode",GRADLE)

    def test_signing_secrets_only_read_in_trusted_mainline_step(self):
        stage=WORKFLOW.split(
            "- name: Prepare optional stable release signing on trusted development branch",1
        )[1].split("- name: Verify mirrored source",1)[0]
        self.assertIn("github.ref == 'refs/heads/develop/android-apk-build'",stage)
        self.assertIn("github.event_name == 'push'",stage)
        for secret in ("FLIPPER_ANDROID_KEYSTORE_B64",
                       "FLIPPER_ANDROID_KEYSTORE_PASSWORD",
                       "FLIPPER_ANDROID_KEY_ALIAS",
                       "FLIPPER_ANDROID_KEY_PASSWORD"):
            self.assertIn("secrets."+secret,stage)
        self.assertIn("base64 --decode",stage)
        self.assertIn("chmod 600",stage)
        self.assertIn("Stable Android signing secrets are incomplete",stage)

    def test_signed_artifact_requires_stable_signing_and_signature_check(self):
        for name in ("Build stable-key signed release candidate",
                     "Verify release APK signature and version",
                     "Upload signed update candidate"):
            stage=WORKFLOW.split("- name: "+name,1)[1].split("- name:",1)[0]
            self.assertIn("if: env.STABLE_SIGNING_ENABLED == 'true'",stage)
        self.assertIn("apksigner",WORKFLOW)
        self.assertIn("versionCode mismatch",WORKFLOW)
        self.assertIn("Remove temporary signing keystore",WORKFLOW)
        self.assertIn("if: always()",WORKFLOW)

    def test_signing_material_not_committed_or_hardcoded(self):
        self.assertIn("FLIPPER_ANDROID_SIGNING_KEYSTORE_PATH",GRADLE)
        self.assertIn("FLIPPER_ANDROID_KEYSTORE_PASSWORD",GRADLE)
        self.assertIn("FLIPPER_ANDROID_KEY_ALIAS",GRADLE)
        self.assertIn("FLIPPER_ANDROID_KEY_PASSWORD",GRADLE)
        self.assertIn("if(hasStableSigning)",GRADLE)
        self.assertIn("signingConfig signingConfigs.stableRelease",GRADLE)
        self.assertNotIn("storePassword '",GRADLE)
        self.assertNotIn("keyPassword '",GRADLE)
        self.assertNotIn(".jks",GRADLE)

    def test_monotonic_release_version_code(self):
        self.assertIn("100000 + GITHUB_RUN_NUMBER",WORKFLOW)
        self.assertIn("FLIPPER_AI_VERSION_CODE",WORKFLOW)
        self.assertIn("ciVersionCode.toInteger()",GRADLE)
        self.assertIn("applicationId 'com.flipperai.satellite'",GRADLE)

if __name__=="__main__": unittest.main()
