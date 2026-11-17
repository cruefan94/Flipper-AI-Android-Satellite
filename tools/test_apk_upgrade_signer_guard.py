import unittest
from apk_upgrade_signer_guard import check_upgrade, parse_signers

A = 'a' * 64
B = 'b' * 64

def report(digest, index=1):
    return f'Signer #{index} certificate SHA-256 digest: {digest}\n'

class SignerGuardTests(unittest.TestCase):
    def test_same_signer(self):
        self.assertTrue(check_upgrade(report(A), report(A.upper())))

    def test_mismatch_blocks(self):
        self.assertFalse(check_upgrade(report(A), report(B)))

    def test_missing_digest_blocks(self):
        with self.assertRaises(ValueError):
            parse_signers('Verified using v2 scheme: true')

    def test_duplicate_signer_blocks(self):
        with self.assertRaises(ValueError):
            parse_signers(report(A) + report(A))

    def test_partial_multisigner_blocks(self):
        with self.assertRaises(ValueError):
            parse_signers(report(A, 2))

    def test_multisigner_mismatch_blocks(self):
        self.assertFalse(check_upgrade(report(A) + report(B, 2), report(A)))

if __name__ == '__main__':
    unittest.main()
