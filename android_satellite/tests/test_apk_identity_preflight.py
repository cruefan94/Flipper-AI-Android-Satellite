#!/usr/bin/env python3
"""Fail-closed APK metadata preflight. Host-only; never installs an APK.

Usage: python3 tests/test_apk_identity_preflight.py --badging FILE --certs FILE
       --variant test|navtest --min-version-code N
Files must be outputs of aapt dump badging and apksigner verify --print-certs.
No keystore, credentials, or installed-app metadata are read.
"""
import argparse
from pathlib import Path
import re
import sys
import unittest

BASE = "com.flipperai.satellite"
EXPECTED = {"test": BASE + ".test", "navtest": BASE + ".test.navtest"}
PACKAGE = re.compile(r"^package: name='([^']+)' versionCode='([0-9]+)'", re.MULTILINE)
SIGNER = re.compile(r"^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})$", re.MULTILINE)


def check(badging, certs, variant, min_version_code):
    """Return a status, never a signer fingerprint or raw tool output."""
    if variant not in EXPECTED:
        return "BLOCKED: unknown test variant"
    matches = PACKAGE.findall(badging)
    if len(matches) != 1:
        return "BLOCKED: missing or ambiguous package metadata"
    package, code = matches[0]
    if package != EXPECTED[variant]:
        return "BLOCKED: APK package identity is not isolated"
    if int(code) < min_version_code or int(code) < 1:
        return "BLOCKED: invalid or stale versionCode"
    if len(SIGNER.findall(certs)) != 1:
        return "BLOCKED: signer certificate evidence missing or ambiguous"
    return "METADATA_PASS_ONLY: isolated package; signer present; no install approval"


class ApkIdentityPreflightTests(unittest.TestCase):
    def setUp(self):
        self.badging = "package: name='com.flipperai.satellite.test' versionCode='87' versionName='test'\n"
        self.certs = "Signer #1 certificate SHA-256 digest: " + "a" * 64 + "\n"

    def test_valid_is_metadata_only(self):
        self.assertTrue(check(self.badging, self.certs, "test", 87).startswith("METADATA_PASS_ONLY"))

    def test_production_package_rejected(self):
        self.assertIn("BLOCKED", check(self.badging.replace(".test'", "'"), self.certs, "test", 1))

    def test_navigation_requires_exact_package(self):
        self.assertIn("BLOCKED", check(self.badging, self.certs, "navtest", 1))
        self.assertTrue(check(self.badging.replace(".test'", ".test.navtest'"), self.certs, "navtest", 1).startswith("METADATA_PASS_ONLY"))

    def test_missing_or_ambiguous_package_rejected(self):
        for value in ("", self.badging + self.badging):
            self.assertIn("BLOCKED", check(value, self.certs, "test", 1))

    def test_signer_required_without_disclosure(self):
        for value in ("", self.certs + self.certs, "Signer #1 certificate SHA-256 digest: invalid\n"):
            result = check(self.badging, value, "test", 1)
            self.assertIn("BLOCKED", result)
            self.assertNotIn("a" * 64, result)

    def test_stale_version_rejected(self):
        self.assertIn("BLOCKED", check(self.badging, self.certs, "test", 88))

    def test_unknown_variant_rejected(self):
        self.assertIn("BLOCKED", check(self.badging, self.certs, "release", 1))


if __name__ == "__main__":
    if len(sys.argv) == 1:
        unittest.main()
    else:
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument("--badging", required=True, type=Path)
        parser.add_argument("--certs", required=True, type=Path)
        parser.add_argument("--variant", choices=tuple(EXPECTED), required=True)
        parser.add_argument("--min-version-code", type=int, required=True)
        args = parser.parse_args()
        try:
            result = check(args.badging.read_text(), args.certs.read_text(), args.variant, args.min_version_code)
        except (OSError, UnicodeError):
            result = "BLOCKED: APK metadata evidence unavailable"
        print(result)
        sys.exit(0 if result.startswith("METADATA_PASS_ONLY") else 1)
