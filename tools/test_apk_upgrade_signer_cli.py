"""Offline CLI regression for APK signer preflight; synthetic reports only."""
import contextlib
import io
import pathlib
import tempfile
import unittest

from apk_upgrade_signer_guard import main

A = "a" * 64
B = "b" * 64


class SignerGuardCliTests(unittest.TestCase):
    def invoke(self, installed, candidate):
        with tempfile.TemporaryDirectory() as tmp:
            p = pathlib.Path(tmp)
            first, second = p / "installed.txt", p / "candidate.txt"
            first.write_text(installed, encoding="utf-8")
            second.write_text(candidate, encoding="utf-8")
            stdout, stderr = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                status = main([str(first), str(second)])
            return status, stdout.getvalue() + stderr.getvalue()

    def test_mismatch_blocks_without_printing_digest(self):
        status, output = self.invoke(
            f"Signer #1 certificate SHA-256 digest: {A}\n",
            f"Signer #1 certificate SHA-256 digest: {B}\n",
        )
        self.assertEqual(status, 1)
        self.assertIn("BLOCKED", output)
        self.assertNotIn(A, output)
        self.assertNotIn(B, output)

    def test_missing_signer_blocks_without_dumping_report(self):
        secret = "DO_NOT_ECHO_PRIVATE_REPORT"
        status, output = self.invoke(secret, f"Signer #1 certificate SHA-256 digest: {A}\n")
        self.assertEqual(status, 2)
        self.assertIn("BLOCKED", output)
        self.assertNotIn(secret, output)

    def test_same_signer_only_reports_preflight_not_approval(self):
        report = f"Signer #1 certificate SHA-256 digest: {A}\n"
        status, output = self.invoke(report, report)
        self.assertEqual(status, 0)
        self.assertIn("NOT upgrade approval", output)
        self.assertNotIn(A, output)


if __name__ == "__main__":
    unittest.main()
