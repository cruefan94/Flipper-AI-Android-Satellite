#!/usr/bin/env python3
"""Fail-closed upstream Momentum source fingerprint preflight.

Run on a CLEAN checkout of the pinned upstream commit before applying overlays.
The fingerprints are Git blob object IDs, not release signatures.
No source files are modified by this check.
"""
from pathlib import Path
import hashlib
import sys

PINNED_MOMENTUM_COMMIT = "d3f89dfe2ef6b01839201598e9be1590cba80322"
EXPECTED_GIT_BLOBS = {
    "applications/services/bt/bt_service/bt.c":
        "b552c9d1eb9f25259eafa83f8f0c1398e60d6314",
    "applications/services/bt/bt_service/bt.h":
        "270922543e0ebabeb7fd52d4f2e5dbe4a61122b4",
    "applications/services/bt/bt_service/bt_i.h":
        "0077f6e516eb9728dd8ce6da20a8001b875c7b6f",
    "applications/services/bt/bt_service/bt_api.c":
        "f0e792d42e770b20dd1009bc080d1a53070f5631",
    "targets/f7/ble_glue/gap.c":
        "38fc5e420014cb1f9f545155e61cbc0cce525268",
    "targets/f7/ble_glue/gap.h":
        "2113a87a55f1338def09f6f22eb72572aa61b916",
    "targets/f7/ble_glue/profiles/serial_profile.c":
        "9bba0d897e92d0dfecedd0a1f575e9856f82b80d",
    "targets/f7/ble_glue/profiles/serial_profile.h":
        "7938bfc33ab67f1c54b95107d045ba9203e2c291",
}

def git_blob_sha(data: bytes) -> str:
    # Git SHA-1 object ID uses "blob <byte-count>\\0" prefix.
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()

def verify(root: Path) -> list[str]:
    mismatches = []
    for rel, expected in EXPECTED_GIT_BLOBS.items():
        target = root / rel
        try:
            raw = target.read_bytes()
        except OSError as err:
            mismatches.append(f"{rel}: unreadable or missing ({err})")
            continue
        actual = git_blob_sha(raw)
        if actual != expected:
            mismatches.append(f"{rel}: blob {actual} != expected {expected}")
    return mismatches

def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python3 momentum_overlay/preflight_pinned_source.py /path/to/clean-Momentum-Firmware", file=sys.stderr)
        return 2
    root = Path(sys.argv[1]).resolve()
    problems = verify(root)
    if problems:
        print("REFUSING overlay: pinned Momentum source files are missing or modified:", file=sys.stderr)
        for item in problems:
            print("  " + item, file=sys.stderr)
        return 1
    print(f"Pinned Momentum {PINNED_MOMENTUM_COMMIT}: 8 source blob fingerprints match.")
    print("This checks version compatibility only, not firmware compilation or runtime safety.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
