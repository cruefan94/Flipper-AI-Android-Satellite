#!/usr/bin/env python3
"""Offline fail-closed comparison of apksigner certificate reports.

Generate each input locally: apksigner verify --print-certs APK > report.txt
Installed APK must come from an owner-authorized read-only export.
No installation, signing, network, or device access occurs.
"""
import argparse
import pathlib
import re
import sys

CERT = re.compile(r'^Signer #([1-9][0-9]*) certificate SHA-256 digest: ([0-9a-fA-F]{64})$', re.M)


def parse_signers(report: str) -> tuple[str, ...]:
    matches = CERT.findall(report)
    if not matches:
        raise ValueError('missing certificate SHA-256 digest')
    indexes = [int(i) for i, _ in matches]
    if indexes != list(range(1, len(indexes) + 1)):
        raise ValueError('duplicate or nonsequential signer records')
    return tuple(d.lower() for _, d in matches)


def check_upgrade(installed: str, candidate: str) -> bool:
    # Conservative same-signer preflight; not Android's full rotation policy.
    return parse_signers(installed) == parse_signers(candidate)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description='Read-only APK signer compatibility preflight')
    parser.add_argument('installed_report', type=pathlib.Path)
    parser.add_argument('candidate_report', type=pathlib.Path)
    args = parser.parse_args(argv)
    try:
        installed = args.installed_report.read_text(encoding='utf-8')
        candidate = args.candidate_report.read_text(encoding='utf-8')
        ok = check_upgrade(installed, candidate)
    except (OSError, UnicodeError, ValueError) as exc:
        print('BLOCKED: cannot establish certificate identity (' + type(exc).__name__ + ')', file=sys.stderr)
        return 2
    if not ok:
        print('BLOCKED: APK signer mismatch; do not replace or uninstall installed app')
        return 1
    print('Signer digest sets match. NOT upgrade approval: verify package, version, lineage, and Android signature policy.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
